package in.bets.smartplug.gcm;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.bets.smartplug.service.ExtraService;
import in.bets.smartplug.service.ServiceIntruderSchedulerCancel;
import in.bets.smartplug.ui.ActivityDialog;
import in.bets.smartplug.ui.ActivityDialogExpire;
import in.bets.smartplug.ui.GreenActivity;
import in.bets.smartplug.ui.PaymentGateway;
import in.bets.smartplug.ui.R;
import in.bets.smartplug.ui.common.CreateGenericNotification;
import in.bets.smartplug.ui.common.CreateNotification;
import in.bets.smartplug.ui.common.CustomSplitActionBarFrag;
import in.bets.smartplug.ui.constants.ConstantsTags;
import in.bets.smartplug.ui.constants.NotificationIDConstants;
import in.bets.smartplug.ui.constants.ServerConstant;
import in.bets.smartplug.ui.db.SharedPrefDB;
import in.bets.smartplug.ui.db.SmartPlugDB;
import in.bets.smartplug.ui.model.Device;
import in.bets.smartplug.ui.parser.GetDevicesParser;
import in.bets.smartplug.ui.parser.SchedulerUpdateParser;
import in.bets.smartplug.utility.BadgeUtils;
import in.bets.smartplug.utility.Logger;
import in.bets.smartplug.utility.ServiceDialog;


public class FireBaseNotificationService extends JobIntentService {
    static final int JOB_ID = 1006;
    // private static final String TAG = ;
    public static final String NOTIF_ACK_TYPE = "ACK";
    private static final String TAG = "FireBaseNotificationService";
    public static String NOTIF_TYPE = NOTIF_ACK_TYPE;
    public static final String REFERSH_DEVICE_ACTION_PAGE = "RefreshDeviceActionPage";
    public static final String NOTIF_TYPE_CANCEL = "CANCEL";
    public static final String NOTIF_TYPE_RESTART = "RESTART";
    public static final String NOTIF_ALERT_TYPE = "ALERT";
    private String paymentGateWay;
    private Device device;
    SharedPrefDB spDB;

    public static int counter;
    private boolean initiator = false;
    Map<String, String> reader;
    RemoteMessage message;
    Bundle bundle;


    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, FireBaseNotificationService.class, JOB_ID, work);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        bundle = intent.getBundleExtra("BundleExtras");
        performAction(bundle);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void performAction(Bundle intent) {
        Log.w("performAction","perform called");
        String actionKey = intent.getString(
                ConstantsTags.ACTION_KEY);
        // below check is done to prevent first time installation crash..
        if (actionKey != null && actionKey != "") {

            /**
             * There are many places where we required context to show dialog
             */
            Context context = FireBaseNotificationService.this.getApplicationContext();
            if (context == null) {
                return;
            }
            spDB = new SharedPrefDB(context);
            String email = spDB.getEmailId();
            if (null == email || email.length() < 3) {
                return;
            }
            // String actionKey = intent.getExtras().getString(
            // ConstantsTags.ACTION_KEY);
            String alertBody = intent.getString(
                    ConstantsTags.ALERT_BODY);
            paymentGateWay = intent.getString(
                    ConstantsTags.paymentGwUrl);
            Logger.i(TAG, "alertBody: " + alertBody);
            //this notification is only for generic purpose no need to go ahead. simply return after successfull
            //notification show.
            if (actionKey.equalsIgnoreCase("generic")) {
                CreateGenericNotification notification = new CreateGenericNotification(
                        context);
                notification.setCustomNotification(getResources()
                                .getString(R.string.app_name), alertBody,
                        R.drawable.icon_app, 101);
                return;
            }
            String badge = intent.getString(ConstantsTags.BADGE);
            String deviceID = null;
            // TESTING : Encoded with try-catch to remove the crash at first
            // time
            try {

                deviceID = actionKey.substring(actionKey.lastIndexOf("_") + 1,
                        actionKey.length());
                Logger.e(TAG, "Device id " + actionKey);
                // -------------------------------------------------------
                device = new SmartPlugDB(context).getDevice(deviceID);
                Logger.e(TAG, "Device=" + " " + device.toString() + "Device id " + deviceID);
                if (device == null)
                    return;
            } catch (NullPointerException ne) {
                ne.printStackTrace();
            }
            /**
             * No need to update DB only show dialog if device is in front
             * otherwise show a notification
             */
            if (actionKey.contains(ConstantsTags.DEVICE_RESET)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.i(TAG, alertBody);

                // DEVICE RESET NOTIFICATION
                /**
                 * Whenever device is deleted we get below notification, now we
                 * will delete device from db .
                 */


                long x = new SmartPlugDB(context).deleteDevice(deviceID);
                if (x > 0) {
                    Intent in = new Intent(
                            GCMNotificationIntentService.SMSLOCALRECEIVER);
                    in.putExtra("action", REFERSH_DEVICE_ACTION_PAGE);
                    LocalBroadcastManager.getInstance(
                            FireBaseNotificationService.this)
                            .sendBroadcast(in);
                }
                if (isApplicationInForeground()) {
                    ServiceDialog.showDialog(context,
                            getResources().getString(R.string.beconnected),
                            alertBody, deviceID);

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }

            } else if (actionKey.contains(ConstantsTags.GCM_DEVICE_SCHEDULE_RESET)) {
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                // DEVICE SCHEDULER UPDATE NOTIFICATION
                Logger.i(TAG, alertBody);
//                updateScheduler();
                updateDB();
                if (isApplicationInForeground()) {
                    Logger.e(TAG, actionKey);
                    ServiceDialog.showDialogTest(context,
                            getResources().getString(R.string.beconnected), alertBody,
                            deviceID);

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }


            } else if (actionKey.contains(ConstantsTags.Expiry_Date)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                // DEVICE SCHEDULER UPDATE NOTIFICATION
                Logger.i(TAG, alertBody);

//                String paymentGwUrl = intent.getExtras().getString(
//                        ConstantsTags.paymentGwUrl);
//                spDB.setPaymentGwUrl(paymentGwUrl);

                if (isApplicationInForeground()) {
                    Logger.e(TAG, actionKey);
//ServiceDialog.showDeviceExpireDialog(context,alertBody,paymentGwUrl);
//                    ServiceDialog.showDialogTest(context,
//                            getResources().getString(R.string.beconnected), alertBody,
//                            deviceID);
                    Intent i = new Intent(context, ActivityDialogExpire.class);
                    i.putExtra("deviceID", deviceID);
                    i.putExtra(getString(R.string.paymentUrl), paymentGateWay);
                    i.putExtra("message", alertBody);

                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);

                } else {
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
//                        CreateNotification notification = new CreateNotification(
//                                context);
//                        notification.setCustomNotification(getResources()
//                                        .getString(R.string.app_name), alertBody,
//                                R.drawable.icon_app, NotificationID.getID());
//
//                    } else {
                    sendPaymentNotification(alertBody, paymentGateWay);
//                    }
                }
                /**
                 * Whenever device motion sensor find intruder then we get below
                 * notification. Now we will updated user by playing a sound
                 * file and also show dialog to reset or turn off motion sensor
                 * .
                 */

            } else if (actionKey.contains(ConstantsTags.GCM_DEVICE_SCHEDULE_UPDATE)) {
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                // DEVICE SCHEDULER UPDATE NOTIFICATION
                Logger.i(TAG, "fghjy" + alertBody);
                updateDB();
                if (isApplicationInForeground()) {
                    SharedPrefDB pref = new SharedPrefDB(context);
//                    if (device.getDeviceScheduledByEmail().equalsIgnoreCase(pref.getEmailId())) {
//                        Logger.e(TAG, "same user");
//                    } else {
                    Logger.e(TAG, "jfhghg" + actionKey);
                    ServiceDialog.showDialogTest(context,
                            getResources().getString(R.string.beconnected), alertBody,
                            deviceID);
//                    }

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }
                /**
                 * Whenever device motion sensor find intruder then we get below
                 * notification. Now we will updated user by playing a sound
                 * file and also show dialog to reset or turn off motion sensor
                 * .
                 */

            } else if (actionKey.contains(ConstantsTags.DEVICE_INTRUDER)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.e(TAG, alertBody);
//                String timestamp1 = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Device Intruder Notification" + " " + timestamp1, false);
                // DEVICE INTRUDER NOTIFICATION NON INITIATOR
                // USE ConstantsTags.DEVICE_INTRUDER_INTERACTING* for
                // interacting
                // notification
//Intent i=new Intent(GCMNotificationIntentService.this,Player.class);
//                startService(i);


                try {
                    MediaPlayer mPlayer = MediaPlayer.create(
                            FireBaseNotificationService.this, R.raw.alarm);
                    mPlayer.start();
//                    String timestamp = BaseActivity.getTimeStamp();
//                    BaseActivity.appendLogMotionSensor("Alarm start" + " " + timestamp, false);

                } catch (Exception e) {
                    e.printStackTrace();

                }

                initiator = device.getDeviceMotionSensorInitiator()
                        .equalsIgnoreCase(
                                new SharedPrefDB(context).getEmailId());
                showDialogToCancelRestartMotionSensor(alertBody, alertBody,
                        deviceID);

            } else if (actionKey.contains(ConstantsTags.DEVICE_ACK)) {
//                Logger.e(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                Logger.e(TAG, "actionKey:" + actionKey);
                String deviceId = intent.getString(
                        ConstantsTags.DEVICE_ID);
                boolean deviceStatus = (intent.getString(
                        ConstantsTags.DEVICE_STATUS).equalsIgnoreCase("ON") ? true : false);
                boolean actionPending = (intent.getString(
                        ConstantsTags.ACTION_PENDING).equalsIgnoreCase("false") ? false : true);
                boolean deviceScheduledStatus = (intent.getString(
                        ConstantsTags.DEVICE_SCHEDULED_STATUS).equalsIgnoreCase("false") ? false : true);
                // DEVICE ACK NOTIFICATION
//                updateDB();
/**
 *  Update DB with the entities in bundle received &
 *  then render UI. No need to hit for GetAllDevice API.
 * */
                if (null != device) {
                    device.setActionPending(actionPending);
                    device.setDeviceID(deviceId);
                    device.setDeviceControlStatus(deviceStatus);
                    device.setDeviceScheduleStatus(deviceScheduledStatus);
                    Logger.e(TAG, "**DEVICE UPDATE ON PUSH NOTIFICATION : DEVICE ID " + device.getDeviceID() +
                            " DEVICE STATUS : " + device.isDeviceControlStatus());
                    // set boolean IS_APP_LAUNCHED false, so that if app is in foreground,
                    // don't hit GetAllDevice API.
                    ConstantsTags.IS_PUSHNOTIF_RECEIVED = true;
                    // update DB
                    new SmartPlugDB(context).updateDevice(device);
//                    updateDB();
//                    String timestamp = BaseActivity.getTimeStamp();
//                    BaseActivity.appendLog("Update DB after Push Notif." + " " + timestamp, false);
                    // now we have to update list of devices if DeviceAction page in
                    // front.
                    // that's why fire a broad cast receiver which locally
                    // registered
//                    Intent intentUpdate = new Intent(
//                            GCMNotificationIntentService.SMSLOCALRECEIVER);
//                    intentUpdate.putExtra("action", REFERSH_DEVICE_ACTION_PAGE);
//                    // You can also include some extra data.
//                    intentUpdate.putExtra("message", "This is my message!");
//                    LocalBroadcastManager.getInstance(
//                            GCMNotificationIntentService.this)
//                            .sendBroadcast(intentUpdate);

                    Intent widgetIntent = new Intent();
                    widgetIntent.setAction(GCMNotificationIntentService.WIDGET_RECEVIER);

                    Bundle bundle = new Bundle();
                    bundle.putSerializable("device",
                            device);
                    bundle.putString("action", GCMNotificationIntentService.WIDGET_RECEVIER);
                    widgetIntent.putExtras(bundle);
                    LocalBroadcastManager.getInstance(
                            FireBaseNotificationService.this)
                            .sendBroadcast(widgetIntent);
//                    String timestamp1 = BaseActivity.getTimeStamp();
//                    BaseActivity.appendLog("Render UI" + " " + timestamp1, false);
                }

                // sendNotificationHeadsUp("HeadsUp");
                if (isApplicationInForeground()) {
                    Logger.e(TAG, "app in forground:");


                    // /*
// * Check for statement "Welcome to the world of betty....", don't show this dialog for particular case.
// * This case is decided by Surbhi & Megha. This case is handled from server side now. No need for check now.
// */
                    if (!alertBody.contains("Welcome to a smarter world"))

//BaseActivity.showNewCustomAlertDialog(GCMNotificationIntentService.this.getApplicationContext(), getResources().getString(R.string.beconnected),
//        alertBody);
                        ServiceDialog.showDialogTest(context,
                                getResources().getString(R.string.beconnected),
                                alertBody,
                                deviceID);

                } else {
                    Logger.e(TAG, "app in background:");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        Logger.e(TAG, "SDK_INT>=JELLY_BEAN:");
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());
                    } else {
                        Logger.e(TAG, "SDK_INT<JELLY_BEAN:");
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.DEVICE_NOT_DELETED)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.i(TAG, alertBody);

                // DEVICE RESET NOTIFICATION
                /**
                 * Whenever device is deleted we get below notification, now we
                 * will delete device from db .
                 */
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
                // new SmartPlugDB(context).deleteDevice(deviceID);
                if (isApplicationInForeground()) {
                    ServiceDialog.showDialog(context,
                            getResources().getString(R.string.beconnected),
                            alertBody, deviceID);

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }

            }
//            else if (actionKey.contains(ConstantsTags.MOTION_SENSOR_STATE_NOT)) {
//                NOTIF_TYPE = NOTIF_ALERT_TYPE;
//                Logger.i(TAG, alertBody);
//
//                // DEVICE RESET NOTIFICATION
//                /**
//                 * Whenever device is deleted we get below notification, now we
//                 * will delete device from db .
//                 */
//
//                new SmartPlugDB(context).deleteDevice(deviceID);
//                if (isApplicationInForeground()) {
//                    ServiceDialog.showDialog(context,
//                            getResources().getString(R.string.beconnected),
//                            alertBody, deviceID);
//
//                } else {
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
//                        CreateNotification notification = new CreateNotification(
//                                context);
//                        notification.setCustomNotification(getResources()
//                                        .getString(R.string.app_name), alertBody,
//                                R.drawable.icon_app, NotificationID.getID());
//
//                    } else {
//                        sendNotification(alertBody);
//                    }
//                }
//
//            }
            else if (actionKey.contains(ConstantsTags.MOTION_SENSOR_STATE_NOT)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.i(TAG, alertBody);

                // DEVICE RESET NOTIFICATION
                /**
                 * Whenever device is deleted we get below notification, now we
                 * will delete device from db .
                 */
                //ConstantsTags.IS_MOTION_SENSOR_STATE_PENDING = false;
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
                if (isApplicationInForeground()) {
                    ServiceDialog.showDialog(context,
                            getResources().getString(R.string.beconnected),
                            alertBody, deviceID);

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }

            } else if (actionKey.contains(ConstantsTags.DEVICE_MASTER_CHANGE)) {
                Logger.i(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                // DEVICE MOTION SENSOR STATE CHANGE NOTIFICATION
//                String timestamp1 = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Motion Sensor Notification" + " " + timestamp1, false);
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
//                String timestamp = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Motion Sensor db update" + " " + timestamp, false);

                if (isApplicationInForeground()) {
                    // sendNotification(alertBody);
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        // ServiceDialog
                        // .showDialog(
                        // context,
                        // getResources().getString(R.string.alert),
                        // alertBody,
                        // getResources().getString(
                        // R.string.check_balance),
                        // getResources().getString(R.string.cancel),
                        // deviceID);
                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);

                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }

                } else {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            CreateNotification notification = new CreateNotification(
                                    context);
                            notification.setCustomNotification(getResources()
                                            .getString(R.string.app_name), alertBody,
                                    R.drawable.icon_app, 100);
                        } else {
                            sendNotification(alertBody);
                        }
                    } else {
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.MOTION_SENSOR_NOT_CONNECTED)) {
                Logger.i(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                // DEVICE MOTION SENSOR STATE CHANGE NOTIFICATION
//                String timestamp1 = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Motion Sensor Notification" + " " + timestamp1, false);
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
//                String timestamp = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Motion Sensor db update" + " " + timestamp, false);
                // ConstantsTags.IS_MOTION_SENSOR_STATE_PENDING = false;
                if (isApplicationInForeground()) {
                    // sendNotification(alertBody);
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        // ServiceDialog
                        // .showDialog(
                        // context,
                        // getResources().getString(R.string.alert),
                        // alertBody,
                        // getResources().getString(
                        // R.string.check_balance),
                        // getResources().getString(R.string.cancel),
                        // deviceID);
                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);

                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }

                } else {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            CreateNotification notification = new CreateNotification(
                                    context);
                            notification.setCustomNotification(getResources()
                                            .getString(R.string.app_name), alertBody,
                                    R.drawable.icon_app, NotificationID.getID());
                        } else {
                            sendNotification(alertBody);
                        }
                    } else {
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.MOTION_SENSOR_STATE)) {
                Logger.i(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                // DEVICE MOTION SENSOR STATE CHANGE NOTIFICATION
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
                //  ConstantsTags.IS_MOTION_SENSOR_STATE_PENDING = false;
                if (isApplicationInForeground()) {
                    // sendNotification(alertBody);
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        // ServiceDialog
                        // .showDialog(
                        // context,
                        // getResources().getString(R.string.alert),
                        // alertBody,
                        // getResources().getString(
                        // R.string.check_balance),
                        // getResources().getString(R.string.cancel),
                        // deviceID);
                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);
                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }
                } else {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            CreateNotification notification = new CreateNotification(
                                    context);
                            notification.setCustomNotification(getResources()
                                            .getString(R.string.app_name), alertBody,
                                    R.drawable.icon_app, NotificationID.getID());
                        } else {
                            sendNotification(alertBody);
                        }
                    } else {
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.DEVICE_USER_BLOCKED)) {
                Logger.i(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ACK_TYPE;
                // DEVICE MOTION SENSOR STATE CHANGE NOTIFICATION
//                String timestamp1 = BaseActivity.getTimeStamp();
//                BaseActivity.appendLogMotionSensor("Motion Sensor Notification" + " " + timestamp1, false);
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();
//                String timestamp = BaseActivity.getTimeStamp();
                //BaseActivity.appendLogMotionSensor("Motion Sensor db update" + " " + timestamp, false);

                if (isApplicationInForeground()) {
                    // sendNotification(alertBody);
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        // ServiceDialog
                        // .showDialog(
                        // context,
                        // getResources().getString(R.string.alert),
                        // alertBody,
                        // getResources().getString(
                        // R.string.check_balance),
                        // getResources().getString(R.string.cancel),
                        // deviceID);
                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);

                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }

                } else {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            CreateNotification notification = new CreateNotification(
                                    context);
                            notification.setCustomNotification(getResources()
                                            .getString(R.string.app_name), alertBody,
                                    R.drawable.icon_app, NotificationID.getID());
                        } else {
                            sendNotification(alertBody);
                        }
                    } else {
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.DEVICE_NON_ACK_MSG)) {
                Logger.i(TAG, alertBody);
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                // DEVICE NON ACK MSG NOTIFICATION
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                // if device fails to process On/Off, set actionPending status to false,
                // device status to old state & update db
//                device.setActionPending(false);
//                Logger.i(TAG, "***-- Device Object ---> " + device);
//                Logger.i(TAG, "***TESTING DEVICE STATUS---> " + device.isDeviceControlStatus());
//                boolean isDeviceStatus = !device.isDeviceControlStatus();
//                device.setDeviceControlStatus(isDeviceStatus/*device.isDeviceControlStatus()*/);

                String deviceId = intent.getString(
                        ConstantsTags.DEVICE_ID);
                boolean deviceStatus = (intent.getString(
                        ConstantsTags.DEVICE_STATUS).equalsIgnoreCase("ON") ? true : false);
                boolean actionPending = (intent.getString(
                        ConstantsTags.ACTION_PENDING).equalsIgnoreCase("false") ? false : true);
                boolean deviceScheduledStatus = (intent.getString(
                        ConstantsTags.DEVICE_SCHEDULED_STATUS).equalsIgnoreCase("false") ? false : true);
/**
 *  Update DB with the entities in bundle received &
 *  then render UI. No need to hit for GetAllDevice API.
 * */
                if (null != device) {
                    device.setActionPending(actionPending);
                    device.setDeviceID(deviceId);
                    device.setDeviceControlStatus(deviceStatus);
                    device.setDeviceScheduleStatus(deviceScheduledStatus);
                    Logger.e(TAG, "**DEVICE UPDATE ON PUSH NOTIFICATION FAILURE : DEVICE ID " + device.getDeviceID() +
                            " DEVICE STATUS : " + device.isDeviceControlStatus());
                    // set boolean IS_APP_LAUNCHED false, so that if app is in foreground,
                    // don't hit GetAllDevice API.
                    ConstantsTags.IS_PUSHNOTIF_RECEIVED = true;
                    // update DB
                    new SmartPlugDB(context).updateDevice(device);
//                    String timestamp = BaseActivity.getTimeStamp();
//                    BaseActivity.appendLog("Update DB after Push Notif." + " " + timestamp, false);
                    // now we have to update list of devices if DeviceAction page in
                    // front.
                    // that's why fire a broad cast receiver which locally
                    // registered
//                    Intent intentUpdate = new Intent(
//                            GCMNotificationIntentService.SMSLOCALRECEIVER);
//                    intentUpdate.putExtra("action", REFERSH_DEVICE_ACTION_PAGE);
//                    // You can also include some extra data.
//                    intentUpdate.putExtra("message", "This is my message!");
//                    LocalBroadcastManager.getInstance(
//                            GCMNotificationIntentService.this)
//                            .sendBroadcast(intentUpdate);
                    Intent widgetIntent = new Intent();
                    widgetIntent.setAction(GCMNotificationIntentService.WIDGET_RECEVIER);

                    Bundle bundle = new Bundle();
                    bundle.putSerializable("device",
                            device);
                    bundle.putString("action", GCMNotificationIntentService.WIDGET_RECEVIER);
                    widgetIntent.putExtras(bundle);
                    LocalBroadcastManager.getInstance(
                            FireBaseNotificationService.this)
                            .sendBroadcast(widgetIntent);
//                    String timestamp1 = BaseActivity.getTimeStamp();
//                    BaseActivity.appendLog("Render UI" + " " + timestamp1, false);
                }

                if (isApplicationInForeground()) {
                    // sendNotification(alertBody);
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {
                        //check balance button removed from the dialog.
                        ServiceDialog.showDialogTest(context, getResources()
                                        .getString(R.string.beconnected), alertBody,


                                deviceID);
//                        ServiceDialog.showDialog(context, getResources()
//                                        .getString(R.string.alert), alertBody,
//                                getResources()
//                                        .getString(R.string.check_balance),
//                                getResources().getString(R.string.cancel),
//                                deviceID);
                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }
                } else {
                    Logger.e(TAG, "app in background:");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        Logger.e(TAG, "SDK_INT>=JELLY_BEAN:");
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());
                    } else {
                        Logger.e(TAG, "SDK_INT<JELLY_BEAN:");
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.DEVICE_SCHEDULER_ON)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.i(TAG, alertBody);

                // DEVICE RESET NOTIFICATION
                /**
                 * Whenever device is deleted we get below notification, now we
                 * will delete device from db .
                 */
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();


                if (isApplicationInForeground()) {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {

                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);

                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }
            } else if (actionKey.contains(ConstantsTags.DEVICE_SCHEDULER_DELETE)) {
                NOTIF_TYPE = NOTIF_ALERT_TYPE;
                Logger.i(TAG, alertBody);

                // DEVICE RESET NOTIFICATION
                /**
                 * Whenever device is deleted we get below notification, now we
                 * will delete device from db .
                 */
                SmartPlugDB db = new SmartPlugDB(
                        FireBaseNotificationService.this);
                Device device = db.getDevice(deviceID);
                updateDB();


                if (isApplicationInForeground()) {
                    if (device.getDeviceConnectionType().equalsIgnoreCase(
                            "Prepaid")) {

                        ServiceDialog
                                .showDialog(context,
                                        getResources()
                                                .getString(R.string.beconnected),
                                        alertBody,
                                        deviceID);

                    } else {
                        ServiceDialog.showDialog(context, getResources()
                                        .getString(R.string.beconnected), alertBody,

                                deviceID);
                    }

                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        CreateNotification notification = new CreateNotification(
                                context);
                        notification.setCustomNotification(getResources()
                                        .getString(R.string.app_name), alertBody,
                                R.drawable.icon_app, NotificationID.getID());

                    } else {
                        sendNotification(alertBody);
                    }
                }
            }


            // Set badge
            if (badge.length() > 0) {
                if (!isApplicationInForeground()) {
                    try {
                        SharedPrefDB pref = new SharedPrefDB(
                                FireBaseNotificationService.this);
                        counter = pref.getNotificationBadge() + Integer.parseInt(badge);

                        counter = counter > 9 ? 10 : counter;
                        pref.setNotificationBadge(counter);
                        BadgeUtils.setBadge(this, counter);
                        Intent in = new Intent(CustomSplitActionBarFrag.LOCAL_RECIEVER_ACTION);
                        in.putExtra(CustomSplitActionBarFrag.LOCAL_RECIEVER_ACTION, CustomSplitActionBarFrag.BADGE_RECIEVED);
                        LocalBroadcastManager.getInstance(
                                FireBaseNotificationService.this)
                                .sendBroadcast(in);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }


    private void updateDB() {
        new Thread(new Runnable() {
            GetDevicesParser getDevicesParser;

            @Override
            public void run() {
                doInBackground();
            }

            public void onPostExecute() {
                // now we have to update list of devices if DeviceAction page in
                // front.
                // that's why fire a broad cast receiver which locally
                // registered
                Intent intent = new Intent(
                        GCMNotificationIntentService.SMSLOCALRECEIVER);
                intent.putExtra("action", REFERSH_DEVICE_ACTION_PAGE);
                // You can also include some extra data.
                intent.putExtra("message", "This is my message!");
                LocalBroadcastManager.getInstance(
                        FireBaseNotificationService.this)
                        .sendBroadcast(intent);
//                String timeStamp = BaseActivity.getTimeStamp();
//                Logger.i("TAG", "**--TIMESTAMP : Step 6 : REsponse for GET ALL DEVICE STATUS API received--> " + timeStamp);
////                String date = DateFormat.format("dd-MM-yyyy hh:mm:ss",timeStamp).toString();
//                BaseActivity.appendLog("GetDevice response " + timeStamp, false);
            }

            public String doInBackground() {
                Logger.i(TAG, "doInBackground updatedDB()");
                getDevicesParser = new GetDevicesParser(
                        FireBaseNotificationService.this);
                SharedPrefDB pref = new SharedPrefDB(
                        FireBaseNotificationService.this);
//                String emailID = pref.getEmailId();
//                String pass = pref.getPassword();
                String userToken = pref.getUserToken();
                String token = pref.getGCMDeviceRegId();

                getDevicesParser.getDataPost(token, userToken,
                        ServerConstant.URL_GET_ALL_DEVICE_STATUS);


                onPostExecute();
                return null;
            }
        }).start();
    }

    private void showDialogToCancelRestartMotionSensor(String alertBody,
                                                       String message, String deviceID) {
//        String timestamp1 = BaseActivity.getTimeStamp();
//        BaseActivity.appendLogMotionSensor("Motion Sensor dialog" + " " + timestamp1, false);

        if (isApplicationInForeground()) {
            //Toast.makeText(getApplicationContext(),"rhfydgsgjjjjjjjjjjjjjjjjjjrf",Toast.LENGTH_LONG).show();
            showDialogForMotionSensor(message, deviceID);
        } else {
            sendNotificationIntruder(alertBody, message, deviceID);
        }

    }

    /**
     *
     */
    private void updateScheduler() {
        Logger.e(TAG, "updateScheduler");
        new Thread(new Runnable() {

            @Override
            public void run() {
                Looper.prepare();
                SchedulerUpdateParser schedulerUpdateParser = new SchedulerUpdateParser(
                        FireBaseNotificationService.this);
                schedulerUpdateParser.getDataPost();
            }
        }).start();

    }

    // On Trial
    //
    public static ArrayList<String> notifications1 = new ArrayList<>();


    private void sendNotification(String message) {
        // Using RemoteViews to bind custom layouts into Notification

        RemoteViews remoteViews = null;

        // for api > 16 (Lollipop & above)
        if ((android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN)) {
            remoteViews = new RemoteViews(getApplication().getPackageName(),
                    R.layout.notification_white);
        } else {
            remoteViews = new RemoteViews(getApplication().getPackageName(),
                    R.layout.notification);
        }

        // Set Notification Title
        String strtitle = /*device.getDeviceName();*/ getString(R.string.app_name);
        // Set Notification Text
        String strtext = message;

        // Open Splash Class on Notification Click
        Intent intent = new Intent(getApplication(), GreenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Open MainActivity.java Activity
        PendingIntent pIntent = PendingIntent.getActivity(getApplication(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.launcher);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplication())
                .setPriority(-1).setLargeIcon(largeIcon)
                // Set Icon
                .setSmallIcon(R.drawable.icon_app)
                // Set Ticker Message
                .setTicker(getString(R.string.app_name))
                // set big text
                .setStyle(
                        new NotificationCompat.BigTextStyle().bigText(strtext))
                // Dismiss Notification
                .setAutoCancel(true)
                // content title
                .setContentTitle(strtitle)
                // Set PendingIntent into Notification
                .setContentIntent(pIntent)
                // Set RemoteViews into Notification
                .setContent(remoteViews);

        // Locate and set the Image into customnotificationtext.xml ImageViews
        remoteViews.setTextViewText(R.id.text_viewTitle, strtitle);
        // notification
        remoteViews.setTextViewText(R.id.text_viewMessage, strtext);
        // Create Notification Manager
        NotificationManager notificationmanager = (NotificationManager) getApplication()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Log.d("Notification", Log.getStackTraceString(new
                Exception("Called from")));

        // Build Notification with Notification Manager
        notificationmanager.notify(0, builder.build());
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void sendNotificationIntruder(String alertBody, String message,
                                          String deviceId) {
        RemoteViews remoteViews = null;
        if ((android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN)) {
            if (initiator) {
                // Set Notification Title
                String strtitle = getString(R.string.app_name);
                // Open NotificationView Class on Notification Click
                Intent intent = new Intent(this, GreenActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                // Open NotificationView.java Activity
                PendingIntent pIntent = PendingIntent.getActivity(this, -1,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
                Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.launcher);
    /*Notification notification = new NotificationCompat.Builder(this)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentTitle(
                    getString(R.string.app_name))
            .setContentText(alertBody).setLargeIcon(largeIcon)
            .setSmallIcon(R.drawable.icon_app)
            .setTicker(getString(R.string.app_name))
            .setAutoCancel(true)
            .setContentIntent(pIntent)
            .setStyle(
                    new NotificationCompat.BigTextStyle()
                            .bigText(alertBody))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setVibrate(new long[]{1000, 900, 700, 500, 500})
            .build();*/
                Context context = FireBaseNotificationService.this.getApplicationContext();
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
                NotificationCompat.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    NotificationChannel channel = new NotificationChannel("118", "push_notifications", NotificationManager.IMPORTANCE_HIGH);
                    notificationManager.createNotificationChannel(channel);
                    builder = new NotificationCompat.Builder(context, "118");
                } else {
                    builder = new NotificationCompat.Builder(context);
                }

                builder.setCategory(Notification.CATEGORY_ALARM)
                        .setContentTitle(
                                getString(R.string.app_name))
                        .setContentText(alertBody).setLargeIcon(largeIcon)
                        .setSmallIcon(R.drawable.icon_app)
                        .setTicker(getString(R.string.app_name))
                        .setAutoCancel(true)
                        .setContentIntent(pIntent)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(alertBody))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setVibrate(new long[]{1000, 900, 700, 500, 500})
                        .build();
                // NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
                Notification notification = builder.build();
                notification.flags |= Notification.FLAG_AUTO_CANCEL;
                Log.d("Notification", Log.getStackTraceString(new
                        Exception("Called from")));


                notificationManager
                        .notify(NotificationIDConstants.INTRUDER_DETECTED,
                                notification);


            } else {
                // Set Notification Title
                String strtitle = getString(R.string.app_name);
                // Open NotificationView Class on Notification Click
                Intent intent = new Intent(this, GreenActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                // Open NotificationView.java Activity
                PendingIntent pIntent = PendingIntent.getActivity(this, -1,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
                Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.launcher);

                Notification notification = new NotificationCompat.Builder(this)
                        // set Notification Category
                        .setCategory(Notification.CATEGORY_ALARM)
                        // set Notification Title
                        .setContentTitle(
                                getString(R.string.app_name))
                        // set Notification body
                        .setContentText(alertBody)
                        .setLargeIcon(largeIcon)
                        // set Notification Icon
                        .setSmallIcon(R.drawable.icon_app)
                        // Set Ticker Message
                        // .setTicker(getString(R.string.notifIntruderDetected))
                        .setTicker("Ticker")
                        // Dismiss Notification
                        .setAutoCancel(true)
                        // Set PendingIntent into Notification
                        .setContentIntent(pIntent)
                        // set Notification style e.g. BigTextStyle, InboxStyle
                        // or BigPictureStyle.
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(alertBody))
                        // set Notification Visibility e.g. if visible on lock
                        // screen or not
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        // set Notification Priority e.g if MAX then always
                        // comes on top in notification panel and thus always
                        // expanded
                        .setPriority(Notification.PRIORITY_MAX)
                        // set vibrate pattern for notification, long[] is
                        // passed in milliseconds for vibrate, sleep, vibrate,
                        // sleep... pattern
                        .setVibrate(new long[]{1000, 900, 700, 500, 1000})
                        .build();
                NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                Log.d("Notification", Log.getStackTraceString(new
                        Exception("Called from")));

                notificationManager
                        .notify(NotificationIDConstants.INTRUDER_DETECTED,
                                notification);

            }

        } else {
            new RemoteViews(getApplication().getPackageName(),
                    R.layout.notification);
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void createNotification() {
        // Prepare intent which is triggered if the
        // notification is selected
        Intent intent = new Intent(this, ServiceIntruderSchedulerCancel.class);
        PendingIntent pIntent = PendingIntent.getService(this, 0, intent, 0);

        // Build notification
        // Actions are just fake
        Notification noti = new Notification.Builder(this)
                .setContentTitle("New mail from " + "test@gmail.com")
                .setContentText("Subject").setSmallIcon(R.drawable.launcher)
                .setContentIntent(pIntent)
                .addAction(R.drawable.scheduler_cancel, "Cancel", pIntent)
                .addAction(R.drawable.scheduler_restart, "Restart", pIntent)
                .build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // hide the notification after its selected
        noti.flags |= Notification.FLAG_AUTO_CANCEL;
        Log.d("Notification", Log.getStackTraceString(new
                Exception("Called from")));

        notificationManager.notify(0, noti);

    }

    private boolean isApplicationInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (topActivity.getPackageName().equals(getPackageName())) {
                Logger.e(TAG, "APP IN FOREGROUND " + getPackageName());
                return true;
            }
        }
        return false;
    }

    // can not show dialog or start activity from service
    // thats why using a static method of MainActivity to get instance of main
    // activity
    private void showDialogForMotionSensor(String message, String deviceID) {
        Context context = getApplicationContext();
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, ActivityDialog.class);
        intent.putExtra("deviceID", deviceID);
        intent.putExtra("message", message);
        intent.putExtra("isIntruder", "Intruder");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void sendPaymentNotification(String message, String paymentUrl) {
        Context context = FireBaseNotificationService.this.getApplicationContext();
        Intent payNowIntent = new Intent(getApplication(), PaymentGateway.class);
        payNowIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        payNowIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        payNowIntent.putExtra(getString(R.string.paymentUrl), paymentUrl);
        payNowIntent.putExtra(PaymentGateway.CALLER, PaymentGateway.PUSH_NOTI);
        PendingIntent pIntentOff = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), payNowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent ppayNowIntent = PendingIntent.getActivity(getApplication(), (int) System.currentTimeMillis(),
                payNowIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intentCancel = new Intent(context, ExtraService.class);
        intentCancel.putExtra(ExtraService.ACTION, ExtraService.ACTION_REMOVE_NOTIFICATION);

        PendingIntent pIntentCancel = PendingIntent.getService(context, (int) System.currentTimeMillis(), intentCancel,
                PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                R.layout.paymntlytdialog);
        remoteViews.setOnClickPendingIntent(R.id.textViewPayNow, ppayNowIntent);
        remoteViews.setOnClickPendingIntent(R.id.textViewPayNow, pIntentCancel);

        Notification noti = new Notification.Builder(context)
                .setSmallIcon(R.drawable.icon_app)
                .setContent(remoteViews)
                .setContentText(message)
                .build();


        noti.bigContentView = remoteViews;
        remoteViews.setTextViewText(R.id.text, message);
        remoteViews.setTextViewText(R.id.title, getString(R.string.app_name));
        remoteViews.setOnClickPendingIntent(R.id.textViewPayNow, pIntentOff);
        remoteViews.setOnClickPendingIntent(R.id.textViewPayNow, pIntentOff);


        RemoteViews smallRemoteView = new RemoteViews(context.getPackageName(), R.layout.paymntlft_small_view);
        noti.contentView = smallRemoteView;
        smallRemoteView.setTextViewText(R.id.text_viewTitle, getString(R.string.app_name));
        smallRemoteView.setTextViewText(R.id.text_viewMessage, message.substring(0, 60) + "...");
        NotificationCompat.Builder builder;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel("118", "push_notifications", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(context, "118");
        } else {
            builder = new NotificationCompat.Builder(context);
        }
        // NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        builder.setSmallIcon(R.drawable.icon_app)
                .setContent(remoteViews)
                .setContentText(message)
                .build();
        notification.bigContentView = remoteViews;
        notification.contentView = smallRemoteView;
        Log.d("Notification", Log.getStackTraceString(new
                Exception("Called from")));

        notificationManager.notify((int) System.currentTimeMillis(), notification);

    }
}