package javalibusb1;

import static javalibusb1.Libusb1UsbControlIrp.*;
import static javalibusb1.Libusb1UsbDevice.*;
import static javax.usb.UsbConst.*;

import javax.usb.*;
import javax.usb.event.*;
import javax.usb.util.*;
import java.util.*;

public class Libusb1UsbPipe implements UsbPipe {

    private final Libusb1UsbEndpoint endpoint;
    private boolean open;

    public Libusb1UsbPipe(Libusb1UsbEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    // -----------------------------------------------------------------------
    // UsbPipe Implementation
    // -----------------------------------------------------------------------

    public void abortAllSubmissions() {
        throw new RuntimeException("Not implemented");
    }

    public void addUsbPipeListener(UsbPipeListener listener) {
        throw new RuntimeException("Not implemented");
    }

    public UsbIrp asyncSubmit(byte[] data) {
        throw new RuntimeException("Not implemented");
    }

    public void asyncSubmit(List list) {
        throw new RuntimeException("Not implemented");
    }

    public void asyncSubmit(UsbIrp irp) {
        throw new RuntimeException("Not implemented");
    }

    public void close() {
        open = false;
    }

    public UsbControlIrp createUsbControlIrp(byte bmRequestType, byte bRequest, short wValue, short wIndex) {
        return new DefaultUsbControlIrp(bmRequestType, bRequest, wValue, wIndex);
    }

    public UsbIrp createUsbIrp() {
        return new DefaultUsbIrp();
    }

    public UsbEndpoint getUsbEndpoint() {
        return endpoint;
    }

    public boolean isActive() {
        return endpoint.getUsbInterface().isActive() && endpoint.getUsbInterface().getUsbConfiguration().isActive();
    }

    public boolean isOpen() {
        return open;
    }

    public void open() throws UsbException {
        if (!isActive()) {
            throw new UsbNotActiveException();
        }

        if (!endpoint.getUsbInterface().isClaimed()) {
            throw new UsbNotClaimedException();
        }

        if (open) {
            throw new UsbException("Already open");
        }
        open = true;
    }

    public void removeUsbPipeListener(UsbPipeListener listener) {
        throw new RuntimeException("Not implemented");
    }

    public int syncSubmit(byte[] data) throws UsbException, UsbNotActiveException, UsbNotOpenException, java.lang.IllegalArgumentException {
        // This is not correct after what I understand from the reference implementation
        UsbIrp irp = new DefaultUsbIrp();
        irp.setData(data);
        return internalSyncSubmit(irp);
    }

    public void syncSubmit(List<UsbIrp> list) throws UsbException, UsbNotActiveException, UsbNotOpenException, java.lang.IllegalArgumentException {
        for (UsbIrp usbIrp : list) {
            syncSubmit(usbIrp);
        }
    }

    public void syncSubmit(UsbIrp irp) throws UsbException, UsbNotActiveException, UsbNotOpenException, java.lang.IllegalArgumentException {
        internalSyncSubmit(irp);
    }

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    private int internalSyncSubmit(UsbIrp irp) throws UsbException {
        // From what I can tell from the API you don't have to open a device to send control packets.

        if (irp instanceof UsbControlIrp) {
            if (endpoint.getType() != ENDPOINT_TYPE_CONTROL) {
                throw new IllegalArgumentException("This is not a control endpoint.");
            }

            internalSyncSubmitControl(endpoint.usbInterface.libusb_device_handle_ptr, createControlIrp((UsbControlIrp) irp));
            return irp.getActualLength();
        }

        if (!isOpen()) {
            throw new UsbNotOpenException();
        }

        if (!isActive()) {
            throw new UsbNotActiveException();
        }

        // 0 means infinite, not sure if that's what a user really want. I think there is an implicit 5 second
        // default where - trygve
        int timeout = 0;

        if (getUsbEndpoint().getType() == ENDPOINT_TYPE_BULK) {
            int transferred = libusb1.bulk_transfer(endpoint.usbInterface.libusb_device_handle_ptr,
                getUsbEndpoint().getUsbEndpointDescriptor().bEndpointAddress(),
                irp.getData(), irp.getOffset(), irp.getLength(), timeout);

            irp.setActualLength(transferred);
            irp.complete();
            return transferred;
        } else if (getUsbEndpoint().getType() == ENDPOINT_TYPE_INTERRUPT) {
            int transferred = libusb1.interrupt_transfer(endpoint.usbInterface.libusb_device_handle_ptr,
                getUsbEndpoint().getUsbEndpointDescriptor().bEndpointAddress(),
                irp.getData(), irp.getOffset(), irp.getLength(), timeout);

            irp.setActualLength(transferred);
            irp.complete();
            return transferred;
        }
        throw new RuntimeException("Transfer type not implemented");
    }
}
