package com.gieseckedevrient.android.SamplePluginTerminal;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.simalliance.openmobileapi.service.ITerminalService;
import org.simalliance.openmobileapi.service.OpenLogicalChannelResponse;
import org.simalliance.openmobileapi.service.SmartcardError;

import java.util.MissingResourceException;
import java.util.NoSuchElementException;

public class DummyTerminal extends Service {

    private static final String LOG_TAG = DummyTerminal.class.getSimpleName();

    /**
     * Terminal service binder.
     */
    private final ITerminalService.Stub mTerminalBinder = new TerminalServiceImplementation();

	/**
     * constructs byte array for C-APDU "MANAGE_CHANNEL (open)"
     * @return C-APDU
     */
    static byte[] commandManageChannel_open() {
    	return new byte[]{(byte)0x00, (byte)0x70, (byte)0x00, (byte)0x00, (byte)0x01};
    }
       
    /**
     * constructs byte array for C-APDU "MANNAGE_CHANNEL (close)"
     * @param iChannel
     * @return C-APDU
     */
    static byte[] commandManageChannel_close(int iChannel) {
    	byte cla = (byte)((iChannel < 4) ? iChannel : 0x40 | (iChannel - 4));
    	return new byte[]{cla, (byte)0x70, (byte)0x80, (byte)iChannel, (byte)0x00};
    }
    
    /**
     * constructs byte array for C-APDU "SELECT"
     * @param iChannel
     * @param aid
     * @return C-APDU
     */
    static byte[] commandSelect(int iChannel, byte[] aid) {
    	byte cla = (byte)((iChannel < 4) ? iChannel : 0x40 | (iChannel - 4));
    	byte[] command = new byte[5 + aid.length];
    	System.arraycopy(new byte[]{cla, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)aid.length}, 0, command, 0, 5);
    	System.arraycopy(aid, 0, command, 5, aid.length);
    	return command;
    }
       
	MockCard mockCard;
	
    @Override
    public IBinder onBind(Intent intent) {
        return mTerminalBinder;
    }

    @Override
    public void onCreate() {
        // DummyTerminal contains a MockCard, that simulates a smart card:
        mockCard = new MockCard();
        // MockCard contains following applet: 
        mockCard.installApplet(new byte[]{(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x00, (byte)0x00}, 
                new Applet(){
                    @Override
                    public byte[] process(byte[] command) throws Throwable {
                        // all this applet does is answer any C-APDU with this R-APDU:
                        return new byte[] { (byte)0xDE, (byte)0xAD, (byte)0xC0, (byte)0xDE, (byte)(command[0] & 0x03)};
                    }
        });
    }

	public byte[] getAtr() {
		return mockCard.getATR(); 
	}

	public boolean isCardPresent() {
		return true;
	}

	public void internalConnect() {
		Log.v(LOG_TAG, "internalConnect");
	}

	public void internalDisconnect() {
		Log.v(LOG_TAG, "internalDisconnect");
	}

	byte[] selectResponse;
	
	public byte[] getSelectResponse() {
		return selectResponse;
	}

	public byte[] internalTransmit(byte[] command) {
		Log.v(LOG_TAG, "internalTransmit: " + MockCard.bytesToHexString(command));
		return mockCard.process(command);
	}

	public OpenLogicalChannelResponse internalOpenLogicalChannel() {
		Log.v(LOG_TAG, "internalOpenLogicalChannel: default applet");
		byte[] response = mockCard.process(commandManageChannel_open());
		if (response.length != 3 || response[1] != (byte)0x90 || response[2] != (byte)0x00) {
			throw new MissingResourceException("Failed to open channel", "", "");
		}
		return new OpenLogicalChannelResponse(response[0], response);
	}

	public OpenLogicalChannelResponse internalOpenLogicalChannel(byte[] aid) {
		Log.v(LOG_TAG, "internalOpenLogicalChannel: AID = " + MockCard.bytesToHexString(aid));
		int iChannel;
		iChannel = internalOpenLogicalChannel().getChannel();
		byte[] response = mockCard.process(commandSelect(iChannel, aid));
		selectResponse = response;
		if (response.length < 2 || response[response.length - 2] != (byte)0x90 || response[response.length - 1] != (byte)0x00) {
			mockCard.process(commandManageChannel_close(iChannel));
			throw new NoSuchElementException("Failed to select AID");
		}
		return new OpenLogicalChannelResponse(iChannel, selectResponse);
	}

	public void internalCloseLogicalChannel(int iChannel) {
		Log.v(LOG_TAG, "internalCloseLogicalChannel: " + iChannel);
		if (iChannel < 0) return;
		mockCard.process(commandManageChannel_close(iChannel));
	}
	/**
     * The Terminal service interface implementation.
     */
    final class TerminalServiceImplementation extends ITerminalService.Stub {

        @Override
        public OpenLogicalChannelResponse internalOpenLogicalChannel(byte[] aid, byte p2, SmartcardError error) throws RemoteException {
            try {
                if (aid == null) {
                    return DummyTerminal.this.internalOpenLogicalChannel();
                } else {
                    return DummyTerminal.this.internalOpenLogicalChannel(aid);
                }
            } catch (Exception e) {
                Log.e("DummyTerminal", "Exception at internalOpenLogicalChannel", e);
                error.set(e);
                return null;
            }
        }

        @Override
        public void internalCloseLogicalChannel(int channelNumber, SmartcardError error)
                throws RemoteException {
            DummyTerminal.this.internalCloseLogicalChannel(channelNumber);
        }

        @Override
        public byte[] internalTransmit(byte[] command, SmartcardError error) throws RemoteException {
            return DummyTerminal.this.internalTransmit(command);
        }

        @Override
        public byte[] getAtr() {
            return DummyTerminal.this.getAtr();
        }

        @Override
        public boolean isCardPresent() throws RemoteException {
            return DummyTerminal.this.isCardPresent();
        }

        @Override
        public byte[] simIOExchange(int fileID, String filePath, byte[] cmd, SmartcardError error)
                throws RemoteException {
            return null;
        }

        @Override
        public String getSeStateChangedAction() {
            return "org.simalliance.openmobileapi.DummyTerminalEvent";
        }
    }
}

