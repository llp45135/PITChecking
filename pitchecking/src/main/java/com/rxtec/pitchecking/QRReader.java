package com.rxtec.pitchecking;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xvolks.jnative.JNative;
import org.xvolks.jnative.Type;
import org.xvolks.jnative.exceptions.NativeException;
import org.xvolks.jnative.pointers.Pointer;
import org.xvolks.jnative.pointers.memory.MemoryBlockFactory;

import com.rxtec.pitchecking.device.AudioDevice;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.device.HoneyWellQRDevice;
import com.rxtec.pitchecking.event.QRCodeReaderEvent;
import com.rxtec.pitchecking.gui.TicketCheckFrame;
import com.rxtec.pitchecking.mq.JmsReceiverTask;
import com.rxtec.pitchecking.utils.CommUtil;
import com.vguang.VguangApi;

public class QRReader implements Runnable {
	private Log log = LogFactory.getLog("DeviceEventListener");
	private static QRReader instance = new QRReader();
	private JNative qrDeviceJNative = null;

	private TicketCheckFrame ticketFrame;

	public TicketCheckFrame getTicketFrame() {
		return ticketFrame;
	}

	public void setTicketFrame(TicketCheckFrame ticketFrame) {
		this.ticketFrame = ticketFrame;
	}

	private int deviceStatus = Config.StartStatus;

	public static void main(String[] args) {
		TicketCheckFrame ticketFrame = new TicketCheckFrame();
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gs = ge.getScreenDevices();
		gs[0].setFullScreenWindow(ticketFrame);
		QRReader qrReader = QRReader.getInstance();
		qrReader.setTicketFrame(ticketFrame);
		ScheduledExecutorService qrReaderScheduler = Executors.newScheduledThreadPool(1);
		qrReaderScheduler.scheduleWithFixedDelay(qrReader, 0, 100, TimeUnit.MILLISECONDS);

		// ExecutorService executer = Executors.newCachedThreadPool();
		// executer.execute(QRReader.getInstance());
	}

	private QRReader() {
		JNative.setLoggingEnabled(false);
		initQRDevice();

		try {
			qrDeviceJNative = new JNative("BAR2unsecurity.dll", "uncompress");
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("Init BAR2unsecurity.dll Failed!", e);
		}
	}

	public static synchronized QRReader getInstance() {
		if (instance == null) {
			instance = new QRReader();
		}
		return instance;
	}

	public void performDeviceCallback(String instr, String year) {
		if (deviceStatus == Config.StartStatus) {
			try {
				Ticket ticket = uncompressTicket(instr, year);
				if (deviceStatus == Config.StartStatus && ticket != null) {

					if (DeviceConfig.getInstance().getVersionFlag() == 1) {// 以下为正式代码
//						if (DeviceEventListener.getInstance().isDealDeviceEvent()) {
							QRCodeReaderEvent qrEvent = new QRCodeReaderEvent(Config.QRReaderEvent);
							qrEvent.setTicket(ticket);
							log.debug("offerDeviceEvent ticket");
							DeviceEventListener.getInstance().offerDeviceEvent(qrEvent);
//						}
					} else {
						ticketFrame.showWaitInputContent(ticket, null, 2, 0); // 仅供测试用
					}
				}
			} catch (NumberFormatException | UnsupportedEncodingException e) {
				log.error("QRReader uncompressTicket", e);
			}
		}
	}

	private Ticket uncompressTicket(String instr, String year)
			throws NumberFormatException, UnsupportedEncodingException {
		byte[] outStrArray = uncompress(instr, year);
		Ticket ticket = buildTicket(outStrArray);
		outStrArray = null;
		// ticket.printTicket();
		return ticket;
	}

	/**
	 * 初始化二维码扫描器
	 */
	private void initQRDevice() {
		log.debug("初始化二维码扫描器...");
		if (DeviceConfig.getInstance().getQrDeviceType().equals("V")) {
			log.debug("启用微光扫描器");
			// 应用设置
			VguangApi.applyDeviceSetting();
			// 打开设备
			VguangApi.openDevice();
		} else if (DeviceConfig.getInstance().getQrDeviceType().equals("H")) {
			log.debug("启用HoneyWell扫描器");
			try {
				HoneyWellQRDevice.getInstance().connect(DeviceConfig.getInstance().getHoneywellQRPort());
				DeviceConfig.getInstance().setQrdeviceStatus(1);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				DeviceConfig.getInstance().setQrdeviceStatus(2);
				log.error("initQRDevice:", e);
			}
		}

	}

	/**
	 * 解析二维码数据为车票票面信息
	 * 
	 * @param ticketStr
	 * @return
	 * @throws NumberFormatException
	 * @throws UnsupportedEncodingException
	 */
	private Ticket buildTicket(byte[] ticketArray) throws NumberFormatException, UnsupportedEncodingException {
		Ticket ticket = new Ticket();
		if (ticketArray.length == 117) {

			String ticketStr = new String(ticketArray, "gbk");

			ticket.setTicketNo(ticketStr.substring(0, 7));
			String fromStationCode = ticketStr.substring(7, 10);
			ticket.setFromStationCode(fromStationCode);
			ticket.setFromStationName(DeviceConfig.getInstance().getStationName(fromStationCode));
			String endStationCode = ticketStr.substring(10, 13);
			ticket.setEndStationCode(endStationCode);
			ticket.setToStationName(DeviceConfig.getInstance().getStationName(endStationCode));
			ticket.setChangeStationCode(ticketStr.substring(13, 16));
			String trainCodeStr = ticketStr.substring(16, 24).trim();
			if (trainCodeStr.length() > 5) {
				ticket.setTrainCode(
						trainCodeStr.substring(0, 1) + String.valueOf(Integer.parseInt(trainCodeStr.substring(1))));
			} else {
				ticket.setTrainCode(String.valueOf(Integer.parseInt(trainCodeStr)));
			}
			ticket.setCoachNo(ticketStr.substring(24, 26));
			ticket.setSeatCode(ticketStr.substring(26, 27));
			ticket.setTicketType(ticketStr.substring(27, 29));
			ticket.setSeatNo(ticketStr.substring(29, 33));
			ticket.setTicketPrice(Integer.parseInt(ticketStr.substring(33, 38)));
			byte[] trainDateArray = new byte[8];
			for (int i = 0; i < 8; i++) {
				trainDateArray[i] = ticketArray[i + 38];
			}
			ticket.setTrainDate(new String(trainDateArray));
			ticket.setChangeFlag(ticketStr.substring(46, 47));
			ticket.setTicketSourceCenter(ticketStr.substring(47, 49));
			ticket.setBzsFlag(ticketStr.substring(49, 50));
			ticket.setSaleOfficeNo(ticketStr.substring(50, 57));
			ticket.setSaleWindowNo(ticketStr.substring(57, 60));
			ticket.setSaleDate(ticketStr.substring(60, 68));
			ticket.setCardType(ticketStr.substring(68, 70));
			ticket.setCardNo(ticketStr.substring(72, 90));

			byte[] passengerNameArray = new byte[20];
			for (int i = 0; i < 20; i++) {
				passengerNameArray[i] = ticketArray[i + 90];
			}
			String ss = new String(passengerNameArray, "gbk");
			ticket.setPassengerName(ss.trim());
			byte[] specialArray = new byte[7];
			for (int i = 0; i < 7; i++) {
				specialArray[i] = ticketArray[i + 110];
			}
			ticket.setSpecialStr(new String(specialArray, "gbk"));

			ticket.setInGateNo(DeviceConfig.getInstance().getGateNo());

			trainDateArray = null;
			passengerNameArray = null;
			specialArray = null;
		}

		return ticket;
	}

	/**
	 * 二维码数据还原
	 * 
	 * @param instr
	 * @param year
	 * @return
	 */
	private byte[] uncompress(String instr, String year) {
		byte[] outStrArray = new byte[117];
		Pointer ticketStrPointer = null;
		try {
			String retval = "-1";
			int i = 0;

			ticketStrPointer = new Pointer(MemoryBlockFactory.createMemoryBlock(117));
			qrDeviceJNative.setParameter(i++, instr);
			qrDeviceJNative.setParameter(i++, ticketStrPointer);
			qrDeviceJNative.setParameter(i++, Type.LONG, year);
			qrDeviceJNative.setRetVal(Type.LONG);
			qrDeviceJNative.invoke();

			retval = qrDeviceJNative.getRetVal();

			// log.debug("uncompress: retval==" + retval);// 获取返回值

			if (retval.equals("0")) {
				// outStr = ticketStrPointer.getAsString();
				// outStr = new String(outStr.getBytes("UTF-8"),"GBK");
				outStrArray = ticketStrPointer.getMemory();
				// log.debug("uncompress：outStrArray.length==" +
				// outStrArray.length);
				String ticketStr = new String(outStrArray, "gbk");
				// log.debug("uncompress: ticketArray==" + ticketStr + "##");
			}
			ticketStrPointer.dispose();

		} catch (NativeException e) {
			log.error("QRReader uncompress", e);
		} catch (IllegalAccessException e) {
			log.error("QRReader uncompress", e);
		} catch (UnsupportedEncodingException e) {
			log.error("QRReader uncompress", e);
		} finally {
			if (ticketStrPointer != null)
				try {
					ticketStrPointer.dispose();
				} catch (NativeException e) {
					log.error("QRReader uncompress", e);
				}
		}
		return outStrArray;
	}

	public void start() {
		deviceStatus = Config.StartStatus;
	}

	public void stop() {
		deviceStatus = Config.StopStatus;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		// log.debug("QRReader running!");
		// while(true){
		// CommUtil.sleep(100);
		// }
	}

}
