package com.bluelink.net.jna;

import java.io.IOException;
import java.io.OutputStream;

public class JnaSocketOutputStream extends OutputStream {
    private final int socket;
    private final WinsockNative lib;

    public JnaSocketOutputStream(int socket) {
        this.socket = socket;
        this.lib = WinsockNative.INSTANCE;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        byte[] workBuffer = b;
        if (off != 0) {
            workBuffer = new byte[len];
            System.arraycopy(b, off, workBuffer, 0, len);
        }

        int res = lib.send(socket, workBuffer, len, 0);
        if (res == WinsockNative.SOCKET_ERROR) {
            throw new IOException("Winsock send error: " + lib.WSAGetLastError());
        }
    }
}
