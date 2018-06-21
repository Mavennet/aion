package org.aion.os;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgGuiLauncher;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;

/** Facilitates launching an instance of the Kernel. */
public class KernelLauncher {
    private final CfgGuiLauncher config;
    private final KernelLaunchConfigurator kernelLaunchConfigurator;
    private final EventBusRegistry eventBusRegistry;
    private final File pidFile;

    private KernelInstanceId currentInstance = null;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    /**
     * Constructor.
     *
     * @see {@link CfgGuiLauncher#AUTODETECTING_CONFIG} if you want Kernel Launcher to auto-detect
     *      the parameters
     */
    public KernelLauncher(CfgGuiLauncher config,
                          EventBusRegistry eventBusRegistry) {
        this(config, new KernelLaunchConfigurator(), eventBusRegistry,
                (config.getKernelPidFile() != null ?
                        new File(config.getKernelPidFile()) :
                        choosePidStorageLocation())
        );
    }

    /** Ctor with injectable parameters for unit testing */
    @VisibleForTesting KernelLauncher(CfgGuiLauncher config,
                                      KernelLaunchConfigurator klc,
                                      EventBusRegistry ebr,
                                      File pidFile) {
        this.config = config;
        this.kernelLaunchConfigurator = klc;
        this.eventBusRegistry = ebr;
        this.pidFile = pidFile;
    }

    /**
     * Launch a separate JVM in a new OS process and within it, run the Aion kernel.  PID of process
     * is persisted to disk.
     *
     * @return if successful, a {@link Optional<Process>} whose value is the Process of the aion.sh
     *         wrapper script; otherwise, {@link Optional#empty()}.
     */
    public Process launch() throws KernelControlException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        kernelLaunchConfigurator.configure(config, processBuilder);

        try {
            Process proc = processBuilder.start();
            setAndPersistPid(waitAndCapturePid(proc));
            return proc;
        } catch (IOException ioe) {
            final String message;
            if(ioe.getCause() instanceof IOException) {
                message = "Could not find the aion.sh script for launching the Aion Kernel.  " +
                        "Check your configuration; or if auto-detection is used, please manually configure.";
            } else {
                message = "Could not start kernel.";
            }
            LOGGER.error(message, ioe);
            throw new KernelControlException(message, ioe);
        }
    }

    @VisibleForTesting long waitAndCapturePid(Process proc) throws KernelControlException {
        try {
            // Note: proc is a reference to a shell script that calls the kernel
            // as a background task.  So here we're blocking until the shell script
            // exits, not until the kernel Java process exits.
            proc.waitFor();
        } catch (InterruptedException ie) {
            String message = "Nohup wrapper launch interrupted; aborting.  The kernel " +
                    "may have already been launched, but we could not capture the PID.";
            LOGGER.error(message, ie);
            throw new KernelControlException(message, ie);
        }

        String pid = null;
        try (
                InputStream is = proc.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
        ) {
            pid = CharStreams.toString(isr).replace("\n", "");
            LOGGER.info("Started kernel with pid = {}", pid);
            return Long.valueOf(pid);
        } catch (IOException | NumberFormatException ex) {
            // If we get here, the stdout from the nohup wrapper script was not what we expected.
            // Either there was a bug in the script or it failed to spawn the Aion kernel.
            String message = String.format("Failed to capture the PID of the Aion kernel " +
                    "process.  Wrapper script exited with shell code %d", proc.exitValue());
            LOGGER.error(message, ex);
            LOGGER.info(String.format("wrapper script stdout was: %s", pid));
            throw new KernelControlException(message, ex);
        }
    }

    /**
     * Look for a Kernel PID that we previously launched and persisted to disk.  If successful,
     * set that PID as the launched kernel instance.
     *
     * @return true if old kernel PID found; false otherwise
     * @throws IOException if old kernel PID file found, but error occurred while trying to read it
     * @throws ClassNotFoundException if old kernel PID file found, but error occurred while trying to read it
     */
    public boolean tryResume() throws ClassNotFoundException, IOException {
        if(hasLaunchedInstance()) {
            throw new IllegalArgumentException("Can't try to resume because there is already an associated instance.");
        }

        if(pidFile.exists() && !pidFile.isDirectory()) {
            try {
                setCurrentInstance(retrieveAndSetPid(pidFile));
                LOGGER.debug("Found old kernel pid = {}", currentInstance.getPid());
                return true;
            } catch (ClassNotFoundException | IOException ex) {
                LOGGER.error("Found old kernel pid file at {}, but failed to deserialize it, " +
                                "so it was ignored.", pidFile.getAbsolutePath(), ex);
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Kill the OS process that the kernel is running in.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void terminate() throws KernelControlException {
        // Implemented by calling UNIX `kill` program on the pid of the process we launched.
        // For the future, should add a terminate function in Aion API that we can call in
        // order for this to be less platform-dependent and more reliable.
        if(!hasLaunchedInstance()) {
            throw new IllegalArgumentException("Trying to terminate when there is no running instance");
        }

        ProcessBuilder processBuilder = new ProcessBuilder()
                .command("kill",
                        String.valueOf(currentInstance.getPid()))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        LOGGER.debug("About to kill pid {}", currentInstance.getPid());
        try {
            int killExitCode = processBuilder.start().waitFor();
            LOGGER.trace("`kill` return code: " + killExitCode);
            removePersistedPid();
        } catch (InterruptedException | IOException ex) {
            String message = "Error killing the Aion kernel process.";
            LOGGER.error(message, ex);
            throw new KernelControlException(message, ex);
        }

        setCurrentInstance(null);
    }

    /**
     * Whether the launcher is associated with a running kernel instance.  This includes instances
     * that KernelLauncher recovered through {@link #tryResume()}.
     *
     * @return whether the launcher is associated with a running kernel instance
     */
    public boolean hasLaunchedInstance() {
        return currentInstance != null;
    }

    private KernelInstanceId retrieveAndSetPid(File pidFile) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(pidFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        return (KernelInstanceId) ois.readObject();
    }

    private KernelInstanceId setAndPersistPid(long pid) throws IOException {
        KernelInstanceId kernel = new KernelInstanceId(pid);
        FileOutputStream fos = new FileOutputStream(pidFile);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(kernel);
        setCurrentInstance(kernel);
        return kernel;
    }

    @VisibleForTesting void removePersistedPid() throws IOException {
        if(pidFile.exists() && pidFile.isFile()) {
            pidFile.delete();
        }
    }

    @VisibleForTesting void setCurrentInstance(KernelInstanceId instance) {
        this.currentInstance = instance;
        if(null == instance) {
            eventBusRegistry.getBus(EventBusRegistry.KERNEL_BUS).post(new KernelProcEvent.KernelTerminatedEvent());
        } else {
            eventBusRegistry.getBus(EventBusRegistry.KERNEL_BUS).post(new KernelProcEvent.KernelLaunchedEvent());
        }
    }

    static File choosePidStorageLocation() {
        return new File("/tmp/kernel-pid");
    }
}