/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.Hex;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * The thread print out sync status
 *
 * @author chris
 */
final class TaskShowStatus implements Runnable {

    private final AtomicBoolean start;

    private final int interval;

    private final AionBlockchainImpl chain;

    private final NetworkStatus networkStatus;

    private final SyncStatics statics;

    private final boolean printReport;
    private final String reportFolder;

    private final Logger p2pLOG;

    TaskShowStatus(final AtomicBoolean _start, int _interval, final AionBlockchainImpl _chain,
        final NetworkStatus _networkStatus, final SyncStatics _statics,
        final boolean _printReport, final String _reportFolder, final Logger _log) {
        this.start = _start;
        this.interval = _interval;
        this.chain = _chain;
        this.networkStatus = _networkStatus;
        this.statics = _statics;
        this.printReport = _printReport;
        this.reportFolder = _reportFolder;
        this.p2pLOG = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (this.start.get()) {
            AionBlock selfBest = this.chain.getBestBlock();
            String selfTd = selfBest.getCumulativeDifficulty().toString(10);

            String status =
                "sync-status avg-import=" + String.format("%.2f", this.statics.getAvgBlocksPerSec())
                    //
                    + " b/s" //
                    + " td=" + selfTd + "/" + networkStatus.getTargetTotalDiff().toString(10) //
                    + " b-num=" + selfBest.getNumber() + "/" + this.networkStatus
                    .getTargetBestBlockNumber() //
                    + " b-hash=" + Hex.toHexString(this.chain.getBestBlockHash()) //
                    + "/" + this.networkStatus.getTargetBestBlockHash() + "";

            p2pLOG.info(status);

            // print to report file
            if (printReport) {
                try {
                    Files.write(
                        Paths.get(reportFolder, System.currentTimeMillis() + "-sync-report.out"),
                        status.getBytes());
                } catch (IOException e) {
                    if (p2pLOG.isDebugEnabled()) {
                        p2pLOG.debug("sync-ss report exception {}", e.toString());
                    }
                }
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("sync-ss shutdown {}");
                }
                return;
            }
        }
        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("sync-ss shutdown");
        }
    }
}
