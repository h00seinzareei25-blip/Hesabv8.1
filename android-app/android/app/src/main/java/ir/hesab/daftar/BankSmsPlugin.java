package ir.hesab.daftar;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@CapacitorPlugin(
    name = "BankSms",
    permissions = {
        @Permission(
            alias = "sms",
            strings = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            }
        )
    }
)
public class BankSmsPlugin extends Plugin {
    private BroadcastReceiver smsReceiver;
    private boolean watching = false;

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("available", true);
        ret.put("platform", "android");
        call.resolve(ret);
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("sms", getPermissionState("sms").toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) {
            JSObject ret = new JSObject();
            ret.put("sms", "granted");
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("sms", call, "smsPermsCallback");
    }

    @PermissionCallback
    private void smsPermsCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("sms", getPermissionState("sms").toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void readInbox(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            call.reject("SMS permission not granted", "PERMISSION_DENIED");
            return;
        }

        long sinceMs = 0;
        if (call.getData() != null && call.getData().has("sinceMs")) {
            sinceMs = call.getData().optLong("sinceMs", 0L);
        }
        int limit = 80;
        if (call.getData() != null && call.getData().has("limit")) {
            limit = Math.max(1, Math.min(300, call.getData().optInt("limit", 80)));
        }

        Set<String> senders = new HashSet<>();
        JSArray arr = call.getArray("senders");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    String s = arr.getString(i);
                    if (s != null && !s.trim().isEmpty()) {
                        senders.add(normalizeSender(s));
                    }
                } catch (JSONException ignored) {}
            }
        }

        List<JSObject> messages = new ArrayList<>();
        Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
        String[] projection = new String[]{
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        };
        String selection = null;
        String[] selectionArgs = null;
        if (sinceMs > 0) {
            selection = Telephony.Sms.DATE + ">?";
            selectionArgs = new String[]{String.valueOf(sinceMs)};
        }
        String sort = Telephony.Sms.DATE + " DESC";

        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(uri, projection, selection, selectionArgs, sort);
            if (c == null) {
                // fallback URI used by some OEMs
                c = getContext().getContentResolver().query(Uri.parse("content://sms/inbox"), projection, selection, selectionArgs, sort);
            }
            if (c != null) {
                int idIdx = c.getColumnIndex(Telephony.Sms._ID);
                if (idIdx < 0) idIdx = c.getColumnIndex("_id");
                int addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS);
                if (addrIdx < 0) addrIdx = c.getColumnIndex("address");
                int bodyIdx = c.getColumnIndex(Telephony.Sms.BODY);
                if (bodyIdx < 0) bodyIdx = c.getColumnIndex("body");
                int dateIdx = c.getColumnIndex(Telephony.Sms.DATE);
                if (dateIdx < 0) dateIdx = c.getColumnIndex("date");
                int scanned = 0;
                while (c.moveToNext() && messages.size() < limit && scanned < 2000) {
                    scanned++;
                    String address = addrIdx >= 0 ? c.getString(addrIdx) : "";
                    if (!senders.isEmpty() && !senderMatches(address, senders)) continue;
                    JSObject msg = new JSObject();
                    msg.put("id", idIdx >= 0 ? c.getString(idIdx) : ("sms-" + scanned));
                    msg.put("address", address == null ? "" : address);
                    msg.put("body", bodyIdx >= 0 ? safe(c.getString(bodyIdx)) : "");
                    msg.put("date", dateIdx >= 0 ? c.getLong(dateIdx) : 0L);
                    messages.add(msg);
                }
            }
        } catch (SecurityException se) {
            call.reject("SMS permission denied by system", "PERMISSION_DENIED");
            return;
        } catch (Exception e) {
            call.reject("Failed to read SMS: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return;
        } finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }

        JSObject ret = new JSObject();
        JSArray out = new JSArray();
        for (JSObject m : messages) out.put(m);
        ret.put("messages", out);
        ret.put("count", messages.size());
        call.resolve(ret);
    }

    @PluginMethod
    public void startWatching(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            call.reject("SMS permission not granted", "PERMISSION_DENIED");
            return;
        }
        ensureReceiver();
        JSObject ret = new JSObject();
        ret.put("watching", watching);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopWatching(PluginCall call) {
        removeReceiver();
        JSObject ret = new JSObject();
        ret.put("watching", false);
        call.resolve(ret);
    }

    private void ensureReceiver() {
        if (watching) return;
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
                Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus == null || pdus.length == 0) return;
                String format = bundle.getString("format");
                StringBuilder body = new StringBuilder();
                String address = "";
                long date = System.currentTimeMillis();
                for (Object pdu : pdus) {
                    SmsMessage sms;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        sms = SmsMessage.createFromPdu((byte[]) pdu);
                    }
                    if (sms == null) continue;
                    if (address.isEmpty()) address = sms.getDisplayOriginatingAddress();
                    body.append(sms.getMessageBody());
                    date = sms.getTimestampMillis();
                }
                JSObject msg = new JSObject();
                msg.put("id", "live-" + date + "-" + Math.abs((address + body).hashCode()));
                msg.put("address", address == null ? "" : address);
                msg.put("body", body.toString());
                msg.put("date", date);
                notifyListeners("smsReceived", msg);
            }
        };
        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        filter.setPriority(999);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(smsReceiver, filter);
        }
        watching = true;
    }

    private void removeReceiver() {
        if (!watching || smsReceiver == null) return;
        try { getContext().unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
        smsReceiver = null;
        watching = false;
    }

    @Override
    protected void handleOnDestroy() {
        removeReceiver();
        super.handleOnDestroy();
    }

    private static String normalizeSender(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static boolean senderMatches(String address, Set<String> senders) {
        String a = normalizeSender(address);
        if (a.isEmpty()) return false;
        for (String s : senders) {
            if (s.isEmpty()) continue;
            if (a.equals(s) || a.contains(s) || s.contains(a)) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
