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
package org.aion.p2p.impl1.tasks;

import org.aion.p2p.Msg;
import org.aion.p2p.impl1.P2pMgr.Dest;

/**
 * An outgoing message.
 */
public class MsgOut {

    private final int nodeId;
    private final String displayId;
    private final int lane;
    private final Msg msg;
    private final Dest dest;
    private final long timestamp;

    /**
     * Constructs an outgoing message.
     *
     * @param nodeId The node id.
     * @param displayId The display id.
     * @param msg The message.
     * @param dest The destination.
     */
    public MsgOut(final int nodeId, final String displayId, final Msg msg, final Dest dest) {
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.msg = msg;
        this.dest = dest;
        this.lane = TaskSend.hash2Lane(nodeId);
        this.timestamp = System.currentTimeMillis();
    }

    public int getNodeId() {
        return this.nodeId;
    }

    String getDisplayId() {
        return this.displayId;
    }

    public Msg getMsg() {
        return this.msg;
    }

    Dest getDest() {
        return this.dest;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    int getLane() {
        return this.lane;
    }
}
