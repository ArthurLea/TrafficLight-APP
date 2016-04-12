/* Project: App Controller for copter 4(including Crazepony)
 * Author: 	Huang Yong xiang 
 * Brief:	This is an open source project under the terms of the GNU General Public License v3.0
 * TOBE FIXED:  1. disconnect and connect fail with Bluetooth due to running thread 
 * 				2. Stick controller should be drawn in dpi instead of pixel.  
 * 
 * */

package com.meng.trafficlight;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class BTClient extends Activity {
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private final static String TAG = BTClient.class.getSimpleName();
    private final static int REQUEST_CONNECT_DEVICE = 1; // 宏定义查询设备句柄
    //BLE模块本身传输速率有限，尽量减少数据发送量
    private final static int WRITE_DATA_PERIOD = 40;//update period，跟新数据周期，40*10ms
    public static boolean systemOk = false;//系统启动标志
    public static boolean enter_stop = false;//系统禁停标志
    private static boolean startConnect = false;//android端开始连接标志
    Handler timeHandler = new Handler(); //定时器周期，用于跟新数据等;
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
//                timeHandler.postDelayed(this, WRITE_DATA_PERIOD);
            } catch (Exception e) {
            }
        }
    };
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService; //BLE收发服务
    // Code to manage Service lifecycle.
    // 管理BLE数据收发服务整个生命周期
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    private boolean mConnected = false;
    private EditText et_time;
    private ImageView iv_run_red_light;
    private Button button_s_n_time, button_e_w_time, button_enter_stop, button_quit_stop, button_ok;
    private TextView tv_device_name, tv_device_address;
    private TextView mDataField, tv_run_red_light, cycle_nums, cycle_time, sou_nor_nums, eas_wes_nums;
    private ScrollView svResult;
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    // 定义处理BLE收发服务的各类事件接收机mGattUpdateReceiver，主要包括下面几种：
    // ACTION_GATT_CONNECTED: 连接到GATT
    // ACTION_GATT_DISCONNECTED: 断开GATT
    // ACTION_GATT_SERVICES_DISCOVERED: 发现GATT下的服务
    // ACTION_DATA_AVAILABLE: BLE收到数据
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int reCmd = -2;

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                showDialog();
                updateUI(mConnected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateUI(mConnected);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                // Show all the supported services and characteristics on the user interface.
                // 获得所有的GATT服务，对于Crazepony的BLE透传模块，包括GAP（General Access Profile），
                // GATT（General Attribute Profile），还有Unknown（用于数据读取）
                mBluetoothLeService.getSupportedGattServices();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                final byte[] dataBytes = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

                if (dataBytes != null && dataBytes.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(dataBytes.length);
                    for (byte byteChar : dataBytes)
                        //stringBuilder.append(String.format("%02X ", byteChar));
                        stringBuilder.append(byteChar);

                    Log.i(TAG, "RX Data:" + stringBuilder);

                    //显示下位机接收的数据

                    if ((mDataField.length() > 500) || mDataField.getText().equals("the lower has already reseted!"))
                        mDataField.setText("");
                    mDataField.append(new String(dataBytes));//能够缩放显示
                    svResult.post(new Runnable() {
                        @Override
                        public void run() {
                            svResult.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                }

                //解析得到的数据，获得命令编号
                reCmd = Protocol.processDataIn(dataBytes, dataBytes.length);
                updateLogData();//update the UI TextView data
                updateUI(mConnected);
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void updateUI(boolean mConnected) {
        if (mConnected) {
            tv_device_name.setText(mDeviceName);
            tv_device_address.setText(mDeviceAddress);
        } else {
            tv_device_name.setText("NULL");
            tv_device_address.setText("NULL");
        }
        invalidateOptionsMenu();
    }

    private void run_red_light(byte[] dataBytes) {
        byte run_red_light_result = dataBytes[3];
        if (run_red_light_result != 0) {
            iv_run_red_light.setImageResource(R.drawable.redlight);
        }
        switch (run_red_light_result) {
            case '0':
                iv_run_red_light.setImageResource(R.drawable.greenlight);
                tv_run_red_light.setText("无车闯红灯");
                break;
            case '1':
                tv_run_red_light.setText("南->北有车闯红灯");
                break;
            case '2':
                tv_run_red_light.setText("南->西有车闯红灯");
                break;
            case '3':
                tv_run_red_light.setText("北->南有车闯红灯");
                break;
            case '4':
                tv_run_red_light.setText("北->东有车闯红灯");
                break;
            case '5':
                tv_run_red_light.setText("东->西有车闯红灯");
                break;
            case '6':
                tv_run_red_light.setText("东->南有车闯红灯");
                break;
            case '7':
                tv_run_red_light.setText("西->南有车闯红灯");
                break;
            case '8':
                tv_run_red_light.setText("西->北有车闯红灯");
                break;
            default:
                break;
        }
    }

    private void showDialog() {
        Toast.makeText(this, "connect success!", Toast.LENGTH_SHORT).show();
    }

    //跟新Log相关的数据，主要是飞控传过来的IMU数据和摇杆值数据
    private void updateLogData() {
        byte[] runRedLight = Protocol.dataRunRedLight;
        byte[] souNorNums = Protocol.dataSouNorNums;
        byte[] easWesNums = Protocol.dataEasWesNums;
        byte[] cycleNums = Protocol.dataCycleNums;
        byte[] cycleTime = Protocol.dataCycleTime;

        Log.d("JACK-dataRunRedLight-2", new String(runRedLight));
        Log.d("JACK-dataSouNorNums-2", new String(souNorNums));
        Log.d("JACK-dataEasWesNums-2", new String(easWesNums));
        Log.d("JACK-dataCycleNums-2", new String(cycleNums));
        Log.d("JACK-dataCycleTime-2", new String(cycleTime));

        if (runRedLight[0] != Protocol.COMMRESETMARK)//红绿灯
            run_red_light(runRedLight);
        if (souNorNums[0] != Protocol.COMMRESETMARK) {
            int s_n_nums = (souNorNums[3] - '0') * 100 + (souNorNums[4] - '0') * 10 + (souNorNums[5] - '0');
            sou_nor_nums.setText(s_n_nums + "辆");
        }
        if (easWesNums[0] != Protocol.COMMRESETMARK) {
            int e_w_nums = (easWesNums[3] - '0') * 100 + (easWesNums[4] - '0') * 10 + (easWesNums[5] - '0');
            eas_wes_nums.setText(e_w_nums + "辆");
        }
        if (cycleNums[0] != Protocol.COMMRESETMARK) {
            int cyc_nums = (cycleNums[3] - '0') * 100 + (cycleNums[4] - '0') * 10 + (cycleNums[5] - '0');
            cycle_nums.setText(cyc_nums + "次");
        }
        if (cycleTime[0] != Protocol.COMMRESETMARK) {
            int cyc_time = (cycleTime[3] - '0') * 100 + (cycleTime[4] - '0') * 10 + (cycleTime[5] - '0');
            cycle_time.setText(cyc_time + "S");
        }
        //复位标志
        if (Protocol.RESETMARK) {
            tv_run_red_light.setText("无");
            iv_run_red_light.setImageResource(R.drawable.greenlight);
            sou_nor_nums.setText("0辆");
            eas_wes_nums.setText("0辆");
            cycle_nums.setText("0次");
            cycle_time.setText("0S");
            this.mDataField.setText("the lower has already reseted!");
            Protocol.RESETMARK = false;
            Protocol.clearData();//清空所有寄存器数据
        }
        //更新开始禁停按钮
        if (enter_stop)//已经开启禁停
        {
            button_enter_stop.setVisibility(View.GONE);
            button_quit_stop.setVisibility(View.VISIBLE);
        } else//关闭禁停
        {
            button_enter_stop.setVisibility(View.VISIBLE);
            button_quit_stop.setVisibility(View.GONE);
        }
        if (Protocol.lower_send_flag) {
            Toast.makeText(this, "系统已启动!", Toast.LENGTH_SHORT).show();
            Protocol.lower_send_flag = false;
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 设置画面为主画面 activity_main.xml


        tv_device_name = (TextView) findViewById(R.id.tv_device_name);
        tv_device_address = (TextView) findViewById(R.id.tv_device_address);
        et_time = (EditText) findViewById(R.id.et_time);
        button_s_n_time = (Button) findViewById(R.id.button_s_n_time);
        button_e_w_time = (Button) findViewById(R.id.button_e_w_time);
        button_enter_stop = (Button) findViewById(R.id.button_enter_stop);
        button_quit_stop = (Button) findViewById(R.id.button_quit_stop);
        button_ok = (Button) findViewById(R.id.button_ok);

        tv_run_red_light = (TextView) findViewById(R.id.tv_run_red_light);
        iv_run_red_light = (ImageView) findViewById(R.id.iv_run_red_light);
        cycle_nums = (TextView) findViewById(R.id.cycle_nums);
        cycle_time = (TextView) findViewById(R.id.cycle_time);
        sou_nor_nums = (TextView) findViewById(R.id.sou_nor_nums);
        eas_wes_nums = (TextView) findViewById(R.id.eas_wes_nums);
        svResult = (ScrollView) findViewById(R.id.svResult);
        mDataField = (TextView) findViewById(R.id.mDataFiled);

        //绑定BLE收发服务mServiceConnection
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //开启IMU数据跟新定时器
        timeHandler.postDelayed(runnable, WRITE_DATA_PERIOD); //每隔1s执行
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        bindService(new Intent(this, BluetoothLeService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //注册BLE收发服务接收机mGattUpdateReceiver
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            Log.d(TAG, "mBluetoothLeService NOT null");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //注销BLE收发服务接收机mGattUpdateReceiver
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //解绑BLE收发服务mServiceConnection
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            if (startConnect)
                menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                //进入扫描页面
                Intent serverIntent = new Intent(this, DeviceScanActivity.class); // 跳转程序设置
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE); // 设置返回宏定义
                return true;
            case R.id.menu_disconnect:
                //断开连接
                startConnect = false;
                mBluetoothLeService.disconnect();
                invalidateOptionsMenu();
                return true;
            default:
                break;
        }
        return true;
    }

    private String judgeEditTime() {
        String data_time = et_time.getText().toString();
        try {
            int time = Integer.parseInt(data_time);
            if ((time <= 0) || (time > 99)) {
                Toast.makeText(this, "内容为<99、>0的数字", Toast.LENGTH_SHORT).show();
                et_time.setText("");
                return null;
            }
        } catch (NumberFormatException nfe) {
            Toast.makeText(this, "内容为<99的数字", Toast.LENGTH_SHORT).show();
            et_time.setText("");
            return null;
        }
        if (data_time.equals("")) {
            Toast.makeText(this, "设置的时间为空", Toast.LENGTH_SHORT).show();
            et_time.setText("");
            return null;
        }
        return data_time;
    }

    private void hideSoftKeyBoard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm.isActive())
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    //南北时间
    public void onSendSNTimeButtonClicked(View v) {
        if (mConnected) {
            if (!systemOk) {
                String data_time = judgeEditTime();
                if (data_time != null) {
                    String command = "$811" + data_time + "0F";
                    btSendBytes(command.getBytes());
                    et_time.setText("");
                }
            } else {
                Toast.makeText(this, "系统已启动，不能设置时间！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未连接设备", Toast.LENGTH_SHORT).show();
        }
        hideSoftKeyBoard(v);
    }

    //东西时间
    public void onSendEWTimeButtonClicked(View v) {
//        String disconnectToast = getResources().getString(R.string.DisconnectToast);
        if (mConnected) {
            if (!systemOk) {
                String data_time = judgeEditTime();
                if (data_time != null) {
                    String command = "$812" + data_time + "0F";
                    btSendBytes(command.getBytes());
                    et_time.setText("");
                }
            } else {
                Toast.makeText(this, "系统已启动，不能设置时间！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未连接设备", Toast.LENGTH_SHORT).show();
        }
        hideSoftKeyBoard(v);
    }

    //开启禁停
    public void onSendEnterStopButtonClicked(View v) {
        String data = "000";
        if (mConnected) {
            if (systemOk) {
                if (!enter_stop) {//没有开启禁停
                    String command = "$82" + data + "0F";
                    btSendBytes(command.getBytes());
                    button_enter_stop.setVisibility(View.GONE);
                    button_quit_stop.setVisibility(View.VISIBLE);
                    enter_stop = true;
                }
            } else {
                Toast.makeText(this, "系统尚未启动！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未连接设备", Toast.LENGTH_SHORT).show();
        }
    }

    //关闭禁停
    public void onSendQuitStopButtonClicked(View v) {
        String data = "000";
        if (mConnected) {
            if (systemOk) {
                if (enter_stop) {//已经禁停
                    String command = "$83" + data + "0F";
                    btSendBytes(command.getBytes());
                    button_enter_stop.setVisibility(View.VISIBLE);
                    button_quit_stop.setVisibility(View.GONE);
                    enter_stop = false;
                }
            } else {
                Toast.makeText(this, "系统尚未启动！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未连接设备", Toast.LENGTH_SHORT).show();
        }
    }

    //开启系统
    public void onSendOKSysButtonClicked(View v) {
        String data = "000";
        String disconnectToast = getResources().getString(R.string.DisconnectToast);

        if (mConnected) {
            if (systemOk) {
                Toast.makeText(this, "系统已经启动！", Toast.LENGTH_SHORT).show();
            } else {
                String command = "$80" + data + "0F";
                btSendBytes(command.getBytes());
                button_enter_stop.setVisibility(View.VISIBLE);
                button_quit_stop.setVisibility(View.GONE);
                systemOk = true;
            }
        } else {
            Toast.makeText(this, disconnectToast, Toast.LENGTH_SHORT).show();
        }
    }

    public void btSendBytes(byte[] data) {
        //当已经连接上时才发送
        if (mConnected) {
            mBluetoothLeService.writeCharacteristic(data);
        }
    }

    // 接收扫描结果，响应startActivityForResult()
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    mDeviceName = data.getExtras().getString(EXTRAS_DEVICE_NAME);
                    mDeviceAddress = data.getExtras().getString(EXTRAS_DEVICE_ADDRESS);

                    Log.i(TAG, "mDeviceName:" + mDeviceName + ",mDeviceAddress:" + mDeviceAddress);

                    //连接该BLE Crazepony模块
                    if (mBluetoothLeService != null) {
                        final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                        Log.d(TAG, "Connect request result=" + result);
                    }

                    startConnect = true;
                    invalidateOptionsMenu();
                }
                break;
            default:
                break;
        }
    }
}