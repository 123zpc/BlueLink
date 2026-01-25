package com.bluelink.net.jna;

import java.io.IOException;
import java.io.InputStream;

public class JnaSocketInputStream extends InputStream {
    private final int socket;
    private final WinsockNative lib;

    public JnaSocketInputStream(int socket) {
        this.socket = socket;
        this.lib = WinsockNative.INSTANCE;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int ret = read(b, 0, 1);
        if (ret == -1)
            return -1;
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        // Winsock recv reads directly into buffer.
        // Need to be careful with offset. recv implementation usually takes a direct
        // pointer or byte[]
        // JNA maps byte[] to C char*.

        // However, JNA byte[] mapping usually assumes starting from index 0.
        // If off > 0, we might need a temporary buffer or use Pointer arithmetic.
        // For simplicity, let's use a temp buffer if off > 0, which is inefficient but
        // safe.

        byte[] workBuffer = b;
        boolean usingTemp = false;
        if (off != 0) {
            workBuffer = new byte[len];
            usingTemp = true;
        }

        int res = lib.recv(socket, workBuffer, len, 0);

        if (res == WinsockNative.SOCKET_ERROR) {
            throw new IOException("Winsock recv error: " + lib.WSAGetLastError());
        }
        if (res == 0) {
            return -1; // Connection closed
        }

        if (usingTemp) {
            System.arraycopy(workBuffer, 0, b, off, res);
        }

        return res;
    }
}
