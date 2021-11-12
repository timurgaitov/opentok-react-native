package com.opentokreactnative.utils;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.opentokreactnative.R;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;

import java.util.List;

public class UsbMonitorWrapper {
    private USBMonitor usbMonitor;
    private boolean permissionRequested = false;
    private UsbDevice usbDevice;
    private USBMonitor.UsbControlBlock controlBlock;
    private USBMonitor.OnDeviceConnectListener externalListener;

    public UsbMonitorWrapper(Context context) {
        usbMonitor = new USBMonitor(context, new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                requestDevicePermission();

                if (externalListener != null) {
                    externalListener.onAttach(device);
                }
            }

            @Override
            public void onDettach(UsbDevice device) {
                usbDevice = null;
                controlBlock = null;
                permissionRequested = false;
                if (externalListener != null) {
                    externalListener.onDettach(device);
                }
            }

            @Override
            public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
                usbDevice = device;
                controlBlock = ctrlBlock;
                if (externalListener != null) {
                    externalListener.onConnect(device, ctrlBlock, createNew);
                }
            }

            @Override
            public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                if (externalListener != null) {
                    externalListener.onDisconnect(device, ctrlBlock);
                }
            }

            @Override
            public void onCancel(UsbDevice device) {
                usbDevice = null;
                controlBlock = null;

                if (externalListener != null) {
                    externalListener.onCancel(device);
                }
            }
        });

        usbMonitor.register();

        List<DeviceFilter> deviceFilter = DeviceFilter
                .getDeviceFilters(context, R.xml.device_filter);

        List<UsbDevice> devices = usbMonitor.getDeviceList(deviceFilter);

        if (!devices.isEmpty()) {
            usbDevice = devices.get(0);
        }
    }

    public USBMonitor.UsbControlBlock getControlBlock() { return controlBlock; }

    private boolean hasPermission(UsbDevice device) {
        try {
            return usbMonitor.hasPermission(device);
        } catch (SecurityException ex) {
            // ignore
        }
        return false;
    }

    public void registerExternalListener(USBMonitor.OnDeviceConnectListener listener) {
        this.externalListener = listener;
    }

    public void unregisterExternalListener() {
        this.externalListener = null;
    }

    public boolean isDeviceAvailable() {
        return usbDevice != null && controlBlock != null && hasPermission(usbDevice);
    }

    public void requestDevicePermission() {
        if (permissionRequested) {
            return;
        }

        permissionRequested = true;
        usbMonitor.requestPermission(usbDevice);
    }

    public void destroy() {
        externalListener = null;
        usbMonitor.destroy();
        usbMonitor = null;
        usbDevice = null;
        controlBlock = null;
    }
}
