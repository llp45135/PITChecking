package com.rxtec.pitchecking;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.device.IDCardDevice;
import com.rxtec.pitchecking.event.IDCardReaderEvent;
import com.rxtec.pitchecking.event.IDeviceEvent;
import com.rxtec.pitchecking.gui.TicketCheckFrame;

public class IDReader implements Runnable {
	private Logger log = LoggerFactory.getLogger("DeviceEventListener");
	IDCardDevice device = IDCardDevice.getInstance();
	private int deviceStatus = Config.StartStatus;
	// 以下ticketFrame仅供测试用
	// TicketCheckFrame ticketFrame;

	// public TicketCheckFrame getTicketFrame() {
	// return ticketFrame;
	// }
	//
	// public void setTicketFrame(TicketCheckFrame ticketFrame) {
	// this.ticketFrame = ticketFrame;
	// }

	private static IDReader instance;

	public static synchronized IDReader getInstance() {
		if (instance == null) {
			instance = new IDReader();
		}
		return instance;
	}

	public int getDeviceStatus() {
		return deviceStatus;
	}

	private IDReader() {
		device.Syn_OpenPort();
	}

	@Override
	public void run() {
		readCard();
	}

	/*
	 * 读二代证数据,填充event 读不到数据返回null
	 */
	private void readCard() {
		if (deviceStatus == Config.StartStatus) {

			// log.debug("开始寻卡...");
			// String openPortResult = device.Syn_OpenPort();
			// if (openPortResult.equals("0")) {
			String findval = device.Syn_StartFindIDCard();
			if (findval.equals("0")) {
				String selectval = device.Syn_SelectIDCard();
				if (selectval.equals("0")) {
					IDCard idCard = device.Syn_ReadBaseMsg();
					if (idCard != null && idCard.getIdNo() != null && idCard.getCardImage() != null
							&& idCard.getCardImageBytes() != null) {
						// ticketFrame.showWaitInputContent(null, idCard, 1);//测试代码

						IDCardReaderEvent readCardEvent = new IDCardReaderEvent();
						readCardEvent.setIdCard(idCard);
						DeviceEventListener.getInstance().offerDeviceEvent(readCardEvent);
					}
				}
			} else {
				// log.debug("没有找到身份证");
			}

			// device.Syn_ClosePort();
			// }
		}
	}

	public void start() {
		deviceStatus = Config.StartStatus;
	}

	public void stop() {
		deviceStatus = Config.StopStatus;
	}

	/**
	 * 为测试用
	 * 
	 * @return
	 */
	private IDCard mockIDCard() {
		IDCard card = new IDCard();
		BufferedImage bi = null;
		try {
			bi = ImageIO.read(new File("C:/DCZ/20160412/llp.jpg"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		card.setCardImage(bi);
		return card;

	}

}
