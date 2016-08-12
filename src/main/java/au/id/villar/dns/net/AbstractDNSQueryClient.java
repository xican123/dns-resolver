/*
 * Copyright 2015-2016 Rafael Villar Villar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.id.villar.dns.net;

import au.id.villar.dns.DNSException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

abstract class AbstractDNSQueryClient implements DNSQueryClient {

    enum Status {
        OPENING,
        CONNECTING,
        SENDING,
        RECEIVING,
        RESULT,
        ERROR,
        CLOSED
    }

    SelectableChannel channel;
    ByteBuffer query;
    ByteBuffer result;
    Status status;

    private ResultListener resultListener;

    @Override
    public boolean startQuery(ByteBuffer query, String address, int port, Selector selector,
            ResultListener resultListener) throws DNSException {

        if(status == Status.CLOSED) throw new DNSException("Already closed");
        if(query.remaining() == 0) throw new DNSException("Empty query");

        try {

            IOException exception = close(channel);
            if(exception != null) throw exception;

            this.resultListener = resultListener;
            this.result = null;
            this.status = Status.OPENING;
            this.query = query;
            return checkIfResultAndNotify(selector, address, port);
        } catch (IOException e) {
            throw new DNSException(e);
        }
    }

    @Override
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public boolean doIO() throws DNSException {
        try {

            return checkIfStatusValidAndResultReady() || checkIfResultAndNotify(null, null, 0);

        } catch(IOException | DNSException e) {
            close(channel);
            status = Status.ERROR;
            throw e instanceof DNSException ? (DNSException)e: new DNSException(e);
        }
    }

    @Override
    public void close() throws IOException {
        IOException exChannel = close(channel);
        channel = null;
        status = Status.CLOSED;
        if(exChannel != null) throw exChannel;
    }

    abstract boolean internalDoIO(Selector selector, String address, int port) throws IOException, DNSException;

    private boolean checkIfResultAndNotify(Selector selector, String address, int port)
            throws IOException, DNSException {
        if(!internalDoIO(selector, address, port)) return false;
        resultListener.result(result);
        return true;
    }

    private IOException close(Channel channel) {
        try {
            if(channel != null && channel.isOpen()) channel.close();
            return null;
        } catch(IOException e) {
            return e;
        }
    }

    private boolean checkIfStatusValidAndResultReady() throws DNSException {
        switch(status) {
            case CLOSED: throw new DNSException("Already closed");
            case ERROR: throw new DNSException("Invalid state");
            case RESULT: return true;
            default: return false;
        }
    }

}
