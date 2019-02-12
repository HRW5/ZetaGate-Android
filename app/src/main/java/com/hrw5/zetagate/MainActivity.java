package com.hrw5.zetagate;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int USB_ARDUINO_ID = 0x2341;
    //private static final int USB_ARDUINO_ID = 0x9025; //ELEGOO
    private static final int USB_PRODUCT_ID = 0x0043;

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private String buffer = "";

    private HomeFragment homeFrag;
    private ClimateFragment climateFrag;
    private DebugFragment debugFrag;

    private AudioManager myAudioManager;

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == USB_ARDUINO_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "USB device detached");
                    stopUsbConnection();
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);
        myAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);


        homeFrag = new HomeFragment();
        climateFrag = new ClimateFragment();
        debugFrag = new DebugFragment();


        loadFragment(homeFrag);


        usbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, filter);
    }

    private boolean loadFragment(Fragment fragment) {
        if(fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        Fragment fragment = null;

        switch (menuItem.getItemId()){
            case R.id.navigation_home:
                fragment = homeFrag;
                break;

            case R.id.navigation_climate:
                fragment = climateFrag;
                break;

            case R.id.navigation_debug:
                fragment = debugFrag;
                break;
        }
        return loadFragment(fragment);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUsbConnection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbDetachedReceiver);
        stopUsbConnection();
    }

    private void startUsbConnection() {
        Map<String, UsbDevice> connectedDevices = usbManager.getDeviceList();

        if (!connectedDevices.isEmpty()) {
            for (UsbDevice device : connectedDevices.values()) {
                if (device.getVendorId() == USB_ARDUINO_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "Device found: " + device.getDeviceName());
                    debug("Device found: " + device.getDeviceName());
                    debug("Device found: " + device.getVendorId());

                    startSerialConnection(device);
                    return;
                }
            }
        }
        Log.w(TAG, "Could not start USB connection - No devices found");
        debug("Could not start USB connection - No devices found");

    }

    private void startSerialConnection(UsbDevice device) {
        Log.i(TAG, "Ready to open USB device connection");
        //Toast.makeText(this, "Ready to open USB device connection", Toast.LENGTH_SHORT).show();

        connection = usbManager.openDevice(device);
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection);
        debug("Starting serial connection");

        if (serialDevice != null) {
            try {
                debug("Opening serial connection");
                if(!serialDevice.isOpen()) {
                    serialDevice.open();
                    serialDevice.setBaudRate(115200);
                    serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                    serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    serialDevice.read(callback);
                    Log.i(TAG, "Serial connection opened");
                    debug("Serial connection opened");
                    //Toast.makeText(this, "Serial connection opened", Toast.LENGTH_SHORT).show();
                } else {
                    //Toast.makeText(this, "Serial connection closed", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.w(TAG, "Cannot open serial connection");
                debug(e.getMessage());
            }

        } else {
            Log.w(TAG, "Could not create Usb Serial Device");
            Toast.makeText(this, "Could not create Usb Serial Device", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
    }

    private void onSerialDataReceived(String payload) {

        Log.i(TAG, "Received: " + payload);

        if (payload.matches("^(\\^).*[,].*$")) {
            payload = payload.substring(payload.indexOf("^") + 1);

            String header = payload.substring(0, payload.indexOf(","));
            String data = payload.substring(payload.indexOf(",") + 1);

            switch (header) {
                case "ir":
                    ir(data);
                    break;

                case "gmlan":
                    gmlan(data);
                    break;

                default:
                    debug("ARDUINO Error: Unknown header \"" + header + "\"");
            }
        } else {
            debug("ARDUINO:  \"" + payload + "\"");
        }
    }

    private UsbSerialInterface.UsbReadCallback callback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            try {
                String dataUtf8 = new String(data, "UTF-8");
                buffer += dataUtf8;
                int index;
                while ((index = buffer.indexOf('\n')) != -1) {
                    final String dataStr = buffer.substring(0, index + 1).trim();
                    buffer = buffer.length() == index ? "" : buffer.substring(index + 1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onSerialDataReceived(dataStr);
                        }
                    });
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Error receiving USB data", e);
                debug("Error receiving USB data");
            }
        }
    };

    private void debug(String text) {
        debugFrag.updateSerialTextView(text + "\n");
    }

    private void sendCommand(String command) {
        String payload = "^" + command + "\n";

        if (serialDevice != null) {
            serialDevice.write(payload.getBytes());
        }

        Log.i(TAG, "ANDROID: ^" + command);
        debug("ANDROID: ^" + command);
    }


    public void btnHazard(View v) {
        sendCommand("hazard");
        //Toast.makeText(this, "Hazard Button", Toast.LENGTH_SHORT).show();
        sampleSerial();
    }

    public void sampleSerial() {


//        onSerialDataReceived("^gmlan,0x100D0060,4,0x00|0x00|0x00|0x01");
//        onSerialDataReceived("^gmlan,0x100D0060,4,0x00|0x00|0x00|0x00");
//        onSerialDataReceived("^gmlan,0x100D0060,4,0x00|0x00|0x00|0x1F");
//        onSerialDataReceived("^gmlan,0x100D0060,4,0x00|0x00|0x00|0x00");
//        onSerialDataReceived("^gmlan,0x100D0060,4,0x20|0x00|0x00|0x00");
//        onSerialDataReceived("^gmlan,0x100D0060,4,0x00|0x00|0x00|0x00");
//        onSerialDataReceived("^ir,0x00FF629D");
//        onSerialDataReceived("^ir,0xFFFFFFFF");
//        onSerialDataReceived("^ir,0xFFFFFFFF");
//        onSerialDataReceived("^ir,0x00FFA857");
//        onSerialDataReceived("^ir,0xFFFFFFFF");
    }


    public void ir(String command) {
        switch (command) {
            case "0x00FF629D":
                debug("INFRARED: Volume Up");
                myAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                break;
            case "0x00FFA857":
                debug("INFRARED: Volume Down");
                myAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                break;
            default:
                debug("INFRARED: Unknown Button " + command);
                break;
        }

        //debug("INFRARED: " + command);
    }

    public void gmlan(String data) {
        try {
            List<String> parsedData = new ArrayList<String>(Arrays.asList(data.split(",")));

            String arbitrationId = parsedData.get(0);
            int numOfBytes = 0;
            List<String> stringData = new ArrayList<String>();

            if(1 < parsedData.size()) {
                numOfBytes = Integer.parseInt(parsedData.get(1));
            }

            if(2 < parsedData.size()) {
                stringData = new ArrayList<String>(Arrays.asList(parsedData.get(2).split("\\|")));
            }

            if (stringData.size() != numOfBytes) {
                throw new Exception("Number of bytes does not match header number!");
            }

            switch (arbitrationId) {
                case "0x100D0060":
                    steeringWheel(stringData);
                    break;
                default:
                    //debug("GMLAN: (Unknown Arbitration ID)");
                    break;
            }


            //debug("GMLAN arbitrationId: " + arbitrationId + " Data: " + byteData.toString());

        } catch(Exception e) {
            debug("GMLAN Error: " + e.getMessage());
        }
    }

    public void steeringWheel(List<String> data) {

        String command = concatData(data);

        switch (command){
            case "00000001":
                debug("GMLAN: (Steering Wheel) Volume Up");
                myAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                break;
            case "0000001F":
                debug("GMLAN: (Steering Wheel) Volume Down");
                myAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                break;
            case "20000000":
                debug("GMLAN: (Steering Wheel) Mute");
                myAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "00000100":
                debug("GMLAN: (Steering Wheel) Scroll Up");
                break;
            case "00001F00":
                debug("GMLAN: (Steering Wheel) Scroll Down");
                break;
            case "01000000":
                debug("GMLAN: (Steering Wheel) Play/Pause");
                break;
            default:
                debug("GMLAN: (Steering Wheel) Unknown Button " + command);
                break;
        }

        //debug("GMLAN: (Steering Wheel) " + concatData(data));
    }

    public String concatData(List<String> data) {
        String concatenatedData = "";

        for (String value : data)
        {
            concatenatedData += value.substring(value.indexOf("x") + 1);
        }

        return concatenatedData;
    }
}
