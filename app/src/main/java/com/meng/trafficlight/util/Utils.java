package com.meng.trafficlight.util;

/**
 * Created by meng on 4/5/2016 0005.
 */
public class Utils {
    private static char findHex(byte b) {
        int t = new Byte(b).intValue();
        t = t < 0 ? t + 16 : t;

        if ((0 <= t) && (t <= 9)) {
            return (char) (t + '0');
        }

        return (char) (t - 10 + 'A');
    }

    public static String ByteToString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        //for (int i = 0; i < bytes.length && bytes[i] != (byte) 0; i++) {
        for (int i = 0; i < bytes.length; i++) {
            //sb.append((char) (bytes[i]));
            sb.append(findHex((byte) ((bytes[i] & 0xf0) >> 4)));
            sb.append(findHex((byte) (bytes[i] & 0x0f)));

        }
        return sb.toString();
    }
}
