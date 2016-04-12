//发送部分写有些麻烦，直接定义一个发送缓冲Byte变长数组，写writeInt8 writeInt16 ，把数据到里头，再统一发，发完清空清长。
//Crazepony APP和飞控之间通信协议使用了MWC飞控协议（MSP，Multiwii Serial Protocol），
//MSP协议格式详见http://www.multiwii.com/wiki/index.php?title=Multiwii_Serial_Protocol

package com.meng.trafficlight;

import android.util.Log;

public class Protocol {
    public static final byte COMMRESETMARK = '@';//数据重置标志
    private static final byte COMMSTARTMARK = '$';//数据开始标志
    private static final byte COMMENDMARK = 'F';//数据结束标志
    private static final byte COMMLENGTH = '8';//命令长度
    public static byte[] dataRev = new byte[8];
    public static byte[] dataRunRedLight = new byte[8];
    public static byte[] dataSouNorNums = new byte[8];
    public static byte[] dataEasWesNums = new byte[8];
    public static byte[] dataCycleNums = new byte[8];
    public static byte[] dataCycleTime = new byte[8];
    public static boolean RESETMARK = false;
//    private static int revLength = 0;
public static boolean lower_send_flag = false;
    private static int currentLength = 0;

    static {
        clearData();
    }

    public static int processDataIn(byte[] dataBytes, int length) {
        for (int i = 0; i < length; i++) {
            byte c = dataBytes[i];
            currentLength++;
            if (dataBytes[i] == COMMSTARTMARK) {//表示新的命令开始
                currentLength = 1;
            } else if (dataBytes[i] == COMMENDMARK) {//表示命令结束
                currentLength = 8;
                dataRev[7] = COMMENDMARK;
            }
            //存放接收内容
            switch (currentLength) {
                case 1://开始标志
                    dataRev[0] = COMMSTARTMARK;
                    break;
                case 2://数据长度
                    dataRev[1] = COMMLENGTH;
                    break;
                case 3://主命令
                    dataRev[2] = c;
                    break;
                case 4://次命令||数据1
                    dataRev[3] = c;
                    break;
                case 5://数据2
                    dataRev[4] = c;
                    break;
                case 6://数据3
                    dataRev[5] = c;
                    break;
                case 7://数据4
                    dataRev[6] = c;
                    break;
                case 8://结束标记
                    dataRev[7] = COMMENDMARK;
                    currentLength = 0;//进行下一次的命令接收
                    break;
                default:
                    break;
            }
            //开始解析得到的数据
            if ((dataRev[0] == COMMSTARTMARK) && (dataRev[7] == COMMENDMARK)) {
                switch (dataRev[2]) {
                    case '0'://start system
                        RESETMARK = false;
                        lower_send_flag = true;//表示是由下位机开启系统
                        BTClient.systemOk = true;
                        break;
                    case '1'://set time
                        break;
                    case '2'://enter stop
                        BTClient.enter_stop = true;
                        break;
                    case '3'://quit stop
                        BTClient.enter_stop = false;
                        break;
                    case '4'://run red light
                        srcDataToOtherData(dataRev, dataRunRedLight, 8);
                        break;
                    case '5'://sou nor nums
                        srcDataToOtherData(dataRev, dataSouNorNums, 8);
                        break;
                    case '6'://eas wea nums
                        srcDataToOtherData(dataRev, dataEasWesNums, 8);
                        break;
                    case '7'://cycle nums
                        srcDataToOtherData(dataRev, dataCycleNums, 8);
                        break;
                    case '8'://cycle time
                        srcDataToOtherData(dataRev, dataCycleTime, 8);
                        break;
                    case '9':
                        RESETMARK = true;
                        BTClient.systemOk = false;
                        break;
                    default:
                        break;
                }

                Log.d("JACK-dataRev", new String(dataRev));
                Log.d("JACK-dataRunRedLight-1", new String(dataRunRedLight));
                Log.d("JACK-dataSouNorNums-1", new String(dataSouNorNums));
                Log.d("JACK-dataEasWesNums-1", new String(dataEasWesNums));
                Log.d("JACK-dataCycleNums-1", new String(dataCycleNums));
                Log.d("JACK-dataCycleTime-1", new String(dataCycleTime));

                clearDataRev();
            }
        }
        return 0;
    }

    public static void clearData() {
        byte[] initData = new byte[8];
        for (int i = 0; i < 8; i++) {
            initData[i] = '@';
        }
        srcDataToOtherData(initData, dataRunRedLight, 8);
        srcDataToOtherData(initData, dataSouNorNums, 8);
        srcDataToOtherData(initData, dataEasWesNums, 8);
        srcDataToOtherData(initData, dataCycleNums, 8);
        srcDataToOtherData(initData, dataCycleTime, 8);
    }
    private static void clearDataRev() {
        for (int i = 0; i < 8; i++) {
            dataRev[i] = '@';
        }
    }

    private static void srcDataToOtherData(byte[] src, byte[] other, int len) {
        for (int i = 0; i < len; i++)
            other[i] = src[i];
    }
}