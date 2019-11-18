package se.mah.ag7416.mqttlampcontroler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//TODO ID,ON,BRIGHTNESS,HUE ; ID,ON,BRIGHTNESS,HUE ; ID,ON,BRIGHTNESS,HUE

/**
 * MainActivity class, holds the GUI components, callback method for MQTT messages
 * and functionality for detecting bluetooth beacons
 */
public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private Button btnListLamps, btnUpdate;
    private TextView tvNameLamp1, tvNameLamp2, tvNameLamp3;
    private TextView tvHueLamp1, tvHueLamp2, tvHueLamp3;
    private TextView tvBrightnessLamp1, tvBrightnessLamp2, tvBrightnessLamp3;
    private TextView tvOnOffLamp1, tvOnOffLamp2, tvOnOffLamp3;
    private SeekBar barHue, barBrightness;
    private Switch btnSwitchOnOff;
    private RadioButton rbLamp1, rbLamp2, rbLamp3, rbLampAll;
    private MQTTHelper mqttHelper;
    private String[] lamp1Array = {"0", "0", "0"};
    private String[] lamp2Array = {"0", "0", "0"};
    private String[] lamp3Array = {"0", "0", "0"};
    private String[] beaconList;
    protected static final String TAG = "LOG";
    private BeaconManager beaconManager;
    private boolean[] lampsChecked; // {false, false, false};
    private ArrayList<Integer> selectedLamps = new ArrayList<>();
    private String switchButtonValue = "0";
    private String brightnessBarValue = "0";
    private String hueBarValue = "0";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private Collection<Beacon> beaconCollection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @TargetApi(Build.VERSION_CODES.M)
                    @RequiresApi(api = Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.bind(this);
        initComponents();
        addListeners();
        startMqtt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
        mqttHelper = null;
    }

    /**
     * Adds action listeners for buttons, bars and check boxes
     */
    private void addListeners() {
        btnListLamps.setOnClickListener(new ButtonListLampsListener());
        btnUpdate.setOnClickListener(new ButtonUpdateListener());
        barHue.setOnSeekBarChangeListener(new BarHueListener());
        barBrightness.setOnSeekBarChangeListener(new BarBrightnessListener());
        btnSwitchOnOff.setOnCheckedChangeListener(new SwitchOnOffListener());

    }

    /**
     * Initializes all the GUI components
     */
    private void initComponents() {
        btnListLamps = findViewById(R.id.btnListLamps);
        btnUpdate = findViewById(R.id.btnUpdate);
        tvNameLamp1 = findViewById(R.id.tvNameLamp1);
        tvNameLamp2 = findViewById(R.id.tvNameLamp2);
        tvNameLamp3 = findViewById(R.id.tvNameLamp3);
        tvHueLamp1 = findViewById(R.id.tvHueLamp1);
        tvHueLamp2 = findViewById(R.id.tvHueLamp2);
        tvHueLamp3 = findViewById(R.id.tvHueLamp3);
        tvBrightnessLamp1 = findViewById(R.id.tvBrightnessLamp1);
        tvBrightnessLamp2 = findViewById(R.id.tvBrightnessLamp2);
        tvBrightnessLamp3 = findViewById(R.id.tvBrightnessLamp3);
        tvOnOffLamp1 = findViewById(R.id.tvOnOffLamp1);
        tvOnOffLamp2 = findViewById(R.id.tvOnOffLamp2);
        tvOnOffLamp3 = findViewById(R.id.tvOnOffLamp3);
        barHue = findViewById(R.id.barHue);
        barBrightness = findViewById(R.id.barBrightness);
        btnSwitchOnOff = findViewById(R.id.switchOnOff);
        rbLamp1 = findViewById(R.id.rbLamp1);
        rbLamp2 = findViewById(R.id.rbLamp2);
        rbLamp3 = findViewById(R.id.rbLamp3);
        rbLampAll = findViewById(R.id.rbLampAll);
    }

    /**
     * Instantiates the MQTTHelper class and sets up the callback for messages from the MQTT server
     */
    private void startMqtt() {
        mqttHelper = new MQTTHelper(getApplicationContext());
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Log.d("LOG", "messageArrived: start");
                String[] lampMessage = message.toString().split(";");
                lamp1Array = lampMessage[0].split(",");
                lamp2Array = lampMessage[1].split(",");
                lamp3Array = lampMessage[2].split(",");
                Log.d("LOG", "messageArrived: run updateValues");
                updateValues();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }
        });
    }

    /**
     * Checks for permission from the user for using the location which is needed for sensing the bluetooth beacons
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, " +
                            "this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    /**
     * Updates the GUI values when a message arrives from the MQTT server.
     */
    private void updateValues() {

        Log.d("LOG", "updateValues: start");
        if (lampsChecked[0]) {
            Log.d("LOG", "updateValues: lamp1");
            tvNameLamp1.setText("1");
            if (lamp1Array[0].equals("1")) tvOnOffLamp1.setText("On");
            if (lamp1Array[0].equals("0")) tvOnOffLamp1.setText("Off");
            tvBrightnessLamp1.setText(lamp1Array[1]);
            tvHueLamp1.setText(lamp1Array[2]);
        }
        if (lampsChecked[1]) {
            Log.d("LOG", "updateValues: lamp2");
            tvNameLamp2.setText("2");
            if (lamp2Array[0].equals("1")) tvOnOffLamp2.setText("On");
            if (lamp2Array[0].equals("0")) tvOnOffLamp2.setText("Off");
            tvBrightnessLamp2.setText(lamp2Array[1]);
            tvHueLamp2.setText(lamp2Array[2]);
        }
        if (lampsChecked[2]) {
            Log.d("LOG", "updateValues: lamp3");
            tvNameLamp3.setText("3");
            if (lamp3Array[0].equals("1")) tvOnOffLamp3.setText("On");
            if (lamp3Array[0].equals("0")) tvOnOffLamp3.setText("Off");
            tvBrightnessLamp3.setText(lamp3Array[1]);
            tvHueLamp3.setText(lamp3Array[2]);
        }
    }

    /**
     * Called when the beacon service is running and ready to accept commands through the BeaconManager
     */
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    beaconCollection = beacons;
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
        }

        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "Beacon detected");
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "No longer detect the beacon");
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                Log.i(TAG, "Switched from seeing/not seeing beacons: " + state);
            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) {
        }
    }

    /**
     * Inner class handling button event for the beacon list pop up,
     * populating the available beacons in a list with check boxes
     */
    private class ButtonListLampsListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (beaconCollection != null) {
                tvBrightnessLamp1.setText("");
                tvBrightnessLamp2.setText("");
                tvBrightnessLamp3.setText("");
                tvHueLamp1.setText("");
                tvHueLamp2.setText("");
                tvHueLamp3.setText("");
                tvNameLamp1.setText("");
                tvNameLamp2.setText("");
                tvNameLamp3.setText("");
                tvOnOffLamp1.setText("");
                tvOnOffLamp2.setText("");
                tvOnOffLamp3.setText("");

                if (beaconCollection.size() > 0) {
                    int i = 0;
                    int j = 0;
                    for (Beacon beacon : beaconCollection) {
                        if (beacon.getId2().toString().equals("1337")) {
                            j++;
                        }
                    }
                    beaconList = new String[j];
                    lampsChecked = new boolean[j];
                    for (Beacon beacon : beaconCollection) {
                        if (beacon.getId2().toString().equals("1337")) {
                            beaconList[i++] = beacon.getId3().toString();
                        }
                    }
                }

                AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
                builderSingle.setTitle("Connect with lamps");

                selectedLamps = new ArrayList<>();

                builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builderSingle.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (int x = 0; x < selectedLamps.size(); x++) {
                            Log.d(TAG, "selectedLamps: " + selectedLamps.get(x));
                            Log.d(TAG, "beaconList: " + beaconList[x]);
                        }
                        for (boolean lamp : lampsChecked) {
                            Log.d(TAG, "lampsChecked: " + lamp);
                        }

                    }
                });
                builderSingle.setMultiChoiceItems(beaconList, lampsChecked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (!selectedLamps.contains(which)) {
                            selectedLamps.add(which);
                        }
                        if (selectedLamps.contains(which) && !isChecked) {
                            selectedLamps.remove(selectedLamps.indexOf(which));
                        }
                        Log.d("LOG", "onClick which: " + which);

                    }
                });

                builderSingle.show();
            }
        }
    }

    /**
     * Inner class handling the update button. Collects the values from the GUI components and
     * sends them via the MQTTHelper class to the MQTT server
     */
    private class ButtonUpdateListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {

            if (rbLamp1.isChecked()) {
                if (!switchButtonValue.equals(lamp1Array[0])) {
                    lamp1Array[0] = switchButtonValue;
                    mqttHelper.sendMessage("1,power," + lamp1Array[0]);
                }
                if (!brightnessBarValue.equals(lamp1Array[1])) {
                    lamp1Array[1] = brightnessBarValue;
                    mqttHelper.sendMessage("1,brightness," + lamp1Array[1]);
                }
                if (!hueBarValue.equals(lamp1Array[2])) {
                    lamp1Array[2] = hueBarValue;
                    mqttHelper.sendMessage("1,hue," + lamp1Array[2]);
                }
            }
            if (rbLamp2.isChecked()) {
                if (!switchButtonValue.equals(lamp2Array[0])) {
                    lamp2Array[0] = switchButtonValue;
                    mqttHelper.sendMessage("2,power," + lamp2Array[0]);
                }
                if (!brightnessBarValue.equals(lamp2Array[1])) {
                    lamp2Array[1] = brightnessBarValue;
                    mqttHelper.sendMessage("2,brightness," + lamp2Array[1]);
                }
                if (!hueBarValue.equals(lamp2Array[2])) {
                    lamp2Array[2] = hueBarValue;
                    mqttHelper.sendMessage("2,hue," + lamp2Array[2]);
                }
            }
            if (rbLamp3.isChecked()) {
                if (!switchButtonValue.equals(lamp3Array[0])) {
                    lamp3Array[0] = switchButtonValue;
                    mqttHelper.sendMessage("3,power," + lamp3Array[0]);
                }
                if (!brightnessBarValue.equals(lamp3Array[1])) {
                    lamp3Array[1] = brightnessBarValue;
                    mqttHelper.sendMessage("3,brightness," + lamp3Array[1]);
                }
                if (!hueBarValue.equals(lamp3Array[2])) {
                    lamp3Array[2] = hueBarValue;
                    mqttHelper.sendMessage("3,hue," + lamp3Array[2]);
                }
            }
            if (rbLampAll.isChecked()) {
                lamp1Array[0] = switchButtonValue;
                lamp2Array[0] = switchButtonValue;
                lamp3Array[0] = switchButtonValue;
                mqttHelper.sendMessage("4,power," + lamp1Array[0]);

                lamp1Array[1] = brightnessBarValue;
                lamp2Array[1] = brightnessBarValue;
                lamp3Array[1] = brightnessBarValue;
                mqttHelper.sendMessage("4,brightness," + lamp1Array[1]);

                lamp1Array[2] = hueBarValue;
                lamp2Array[2] = hueBarValue;
                lamp3Array[2] = hueBarValue;
                mqttHelper.sendMessage("4,hue," + lamp1Array[2]);

            }
            updateValues();
        }
    }

    /**
     * Inner class handling the events for the bar which sets the Hue value
     */
    private class BarHueListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            hueBarValue = String.valueOf(progress * 6550);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }

    /**
     * Inner class handling the events for the bar which sets the Brightness value.
     */
    private class BarBrightnessListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            brightnessBarValue = String.valueOf(progress * 25);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }

    /**
     * Inner class for handling the events for switching the lamp on or off
     */
    private class SwitchOnOffListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                switchButtonValue = "1";
            } else {
                switchButtonValue = "0";
            }
        }
    }
}