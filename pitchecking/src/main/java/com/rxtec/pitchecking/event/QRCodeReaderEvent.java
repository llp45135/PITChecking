package com.rxtec.pitchecking.event;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.Ticket;

public class QRCodeReaderEvent implements IDeviceEvent {

	int eventType = Config.QRReaderEvent;
	private Ticket ticket;

	public void setTicket(Ticket ticket) {
		this.ticket = ticket;
	}

	public QRCodeReaderEvent(int t) {
		eventType = t;
	}

	@Override
	public int getEventType() {
		// TODO Auto-generated method stub
		return eventType;
	}

	@Override
	public Object getData() {
		// TODO Auto-generated method stub
		return ticket;
	}

}
