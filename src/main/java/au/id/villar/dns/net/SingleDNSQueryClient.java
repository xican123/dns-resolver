/*
 * Copyright 2015 Rafael Villar Villar
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
import au.id.villar.dns.engine.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

@Deprecated
public class SingleDNSQueryClient {

    private enum Status {
        START_QUERY_UDP,
        QUERY_UDP,
        EVAL_UDP,
        START_QUERY_TCP,
        QUERY_TCP,
        RESULT,
        ERROR,
        CLOSED
    }

    private final UDPDNSQueryClient udpClient;
    private final TCPDNSQueryClient tcpClient;

    private Selector selector;
    private String address;
    private int port;
    private ByteBuffer query;
    private ByteBuffer result;
    private Status status;

    public SingleDNSQueryClient() throws IOException {
        this.udpClient = new UDPDNSQueryClient();
        this.tcpClient = new TCPDNSQueryClient();
    }

    public boolean startQuery(ByteBuffer question, String address, int port, Selector selector, ResultListener resultListener) throws DNSException {

        if(status == Status.CLOSED) throw new DNSException("Already closed");

        this.selector = selector;
        this.address = address;
        this.port = port;
        this.query = question;

        status = (question.position() > UDP_DATAGRAM_MAX_SIZE)? Status.START_QUERY_TCP: Status.START_QUERY_UDP;

        return internalDoIO();

    }

    @Override
    public boolean doIO() throws DNSException {
        try {

            switch(status) {
                case CLOSED: throw new DNSException("Already closed");
                case ERROR: throw new DNSException("Invalid state");
                case RESULT: return true;
            }

            return internalDoIO();
        } catch(DNSException e) {
            status = Status.ERROR;
            throw e;
        }

    }

    @Override
    public ByteBuffer getResult() {
        return result;
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;
        try { udpClient.close(); } catch (IOException e) { exception = e; }
        try { tcpClient.close(); } catch (IOException e) { if(exception == null) exception = e; }
        if(exception != null) throw exception;
    }

    private boolean internalDoIO() throws DNSException {

        switch (status) {

            case START_QUERY_UDP:

                if (!udpClient.startQuery(query, address, port, selector)) {
                    status = Status.QUERY_UDP;
                    return false;
                }
                status = Status.EVAL_UDP;

            case QUERY_UDP:

                if (status == Status.QUERY_UDP && !udpClient.doIO()) return false;

            case EVAL_UDP:

                if (!udpIsTruncated()) {
                    status = Status.RESULT;
                    result = udpClient.getResult();
                    return true;
                }

            case START_QUERY_TCP:

                if(!tcpClient.startQuery(query, address, port, selector)) {
                    status = Status.QUERY_TCP;
                    return false;
                }
                status = Status.RESULT;
                result = tcpClient.getResult();
                return true;

            case QUERY_TCP:

                if (!tcpClient.doIO()) return false;
                status = Status.RESULT;
                result = tcpClient.getResult();

        }

        return true;
    }

    private boolean udpIsTruncated() {
        return (Utils.getInt(udpClient.getResult().array(), 2, 2) & 0x0200) != 0;
    }
}
