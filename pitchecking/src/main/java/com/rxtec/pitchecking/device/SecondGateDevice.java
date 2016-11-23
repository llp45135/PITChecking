package com.rxtec.pitchecking.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.rxtec.pitchecking.DeviceEventListener;
import com.rxtec.pitchecking.EmerButtonTask;
import com.rxtec.pitchecking.ScreenCmdEnum;
import com.rxtec.pitchecking.TicketCheckScreen;
import com.rxtec.pitchecking.TicketVerifyScreen;
import com.rxtec.pitchecking.domain.EmerButtonEvent;
import com.rxtec.pitchecking.event.ScreenElementModifyEvent;
import com.rxtec.pitchecking.mqtt.GatCtrlSenderBroker;
import com.rxtec.pitchecking.utils.CommUtil;
import com.rxtec.pitchecking.utils.CalUtils;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * 闸门控制及指示灯控制
 * 
 * @author lenovo
 *
 */
public class SecondGateDevice implements SerialPortEventListener {
	private Log log = LogFactory.getLog("DeviceEventListener");
	private static SecondGateDevice instance;
	private SerialPort serialPort;
	private InputStream in;
	private OutputStream out;
	private byte[] buffer = new byte[1024];
	private StringBuffer readBuffer = new StringBuffer();
	private static int retryTimes = 3;
	private static int delayTime = 30;
	private static int delayCount = 1000;

	public static synchronized SecondGateDevice getInstance() {
		if (instance == null) {
			instance = new SecondGateDevice();
		}
		return instance;
	}

	private SecondGateDevice() {
	}

	/**
	 * 打开串口
	 * 
	 * @param portName
	 * @throws Exception
	 */
	public void connect(String portName) throws Exception {
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
		if (portIdentifier.isCurrentlyOwned()) {
			System.err.println("Error: Port is currently in use");
		} else {
			CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

			if (commPort instanceof SerialPort) {
				serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(DeviceConfig.getInstance().getGateCrtlRate(), SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

				this.in = serialPort.getInputStream();
				this.out = serialPort.getOutputStream();

				serialPort.addEventListener(this);
				serialPort.notifyOnDataAvailable(true);
				serialPort.notifyOnBreakInterrupt(true);
				log.debug("串口已经打开:" + portName);

			} else {
				System.err.println("Error: Only serial ports are handled by this example.");
			}
		}
	}

	/**
	 * 关闭串口
	 */
	public void close() {
		this.serialPort.close();
	}

	/**
	 * 电磁开关侧门
	 * 
	 * @return
	 */
	public int openEmerDoor() {
		try {
			String cmd = "2A500123";
			log.debug("openSecondDoor cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 开第二道门
	 * 
	 * @return
	 */
	public int openTheSecondDoor() {
		try {
			String cmd = "2A040123";
			log.debug("openThirdDoor cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (入口箭头指示灯为 箭头)
	 * 
	 * @return
	 */
	public int LightEntryArrow() {
		try {
			String cmd = "2A510123";
			log.debug("LightEntryArrow cmd==" + cmd);
			readBuffer.delete(0, readBuffer.length());
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (入口箭头指示灯为 红色‘X’)
	 * 
	 * @return
	 */
	public int LightEntryCross() {
		try {
			String cmd = "2A510023";
			log.debug("LightEntryCross cmd==" + cmd);
			readBuffer.delete(0, readBuffer.length());
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (出口箭头指示灯为 箭头)
	 * 
	 * @return
	 */
	public int LightExitArrow() {
		try {
			String cmd = "2A520123";
			log.debug("LightExitArrow cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (出口箭头指示灯为红色‘X’)
	 * 
	 * @return
	 */
	public int LightExitCross() {
		try {
			String cmd = "2A520023";
			log.debug("LightExitCross cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (玻璃门提示指示灯收到指令后红绿闪烁)
	 */
	public int LightGlassDoorLED() {
		try {
			String cmd = "2A530123";
			log.debug("LightGlassDoorLED cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (顶盖指示灯变绿色)
	 */
	public int LightGreenLED() {
		try {
			String cmd = "2A540123";
			log.debug("LightGreenLED cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 主机---->闸机 (顶盖指示灯变为红色)
	 */
	public int LightRedLED() {
		try {
			String cmd = "2A540023";
			log.debug("LightRedLED cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 获取通道状态函数，此函数功能是检查通道内是否有人闯闸的 第一道门检测有没没刷卡进入通道的反馈 主机---->闸机
	 * (控制板收到此命令时查一下光电感应器的状态。) 2a 58 01 23 主机收到指令后返回 闸机--->主机 2a 58 xx 23 其中XX
	 * 0x00代表正常，没有闯入，0x01代表异常，有未刷卡闯入
	 */
	public int getGateStatus() {
		try {
			String cmd = "2A580123";
			log.debug("getGateStatus cmd==" + cmd);
			byte[] cmdData = CommUtil.hexStringToBytes(cmd);
			for (int i = 0; i < cmdData.length; i++) {
				this.out.write(cmdData[i]);
			}
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		// TODO Auto-generated method stub
		switch (event.getEventType()) {
		case SerialPortEvent.BI:
		case SerialPortEvent.OE:
		case SerialPortEvent.FE:
		case SerialPortEvent.PE:
		case SerialPortEvent.CD:
		case SerialPortEvent.CTS:
		case SerialPortEvent.DSR:
		case SerialPortEvent.RI:
		case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
			break;
		case SerialPortEvent.DATA_AVAILABLE:
			try {
				while (this.in.available() > 0) {
					int b = this.in.read();
					String data = Integer.toHexString(b).toUpperCase();
					if (data.length() < 2) {
						data = "0" + data;
					}
					readBuffer.append(data);
				}

				for (int i = 1; i < retryTimes; i++) {
					CommUtil.sleep(delayTime);
					if (readBuffer.length() == 8) {
						break;
					}
				}

				String ss = readBuffer.toString();
				log.debug("retData==" + ss);

				if (ss.length() >= 8) {
					if (ss.indexOf("2A040023") == 0) {
						// log.debug("第二道闸门已经关闭");
						if (DeviceEventListener.getInstance().isStartThread()) {

							TicketVerifyScreen.getInstance().offerEvent(new ScreenElementModifyEvent(0,
									ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null)); // 恢复初始界面
							DeviceEventListener.getInstance().setDeviceReader(true); // 允许寻卡
							DeviceEventListener.getInstance().setDealDeviceEvent(true); // 允许处理新的事件
							
							if(!DeviceConfig.getInstance().isAllowOpenSecondDoor()){
								GatCtrlSenderBroker.getInstance(DeviceConfig.GAT_MQ_Verify_CLIENT)
								.sendDoorCmd("PITEventTopic", DeviceConfig.Close_SECONDDOOR_Jms);
								DeviceConfig.getInstance().setAllowOpenSecondDoor(true);
							}
							log.debug("人证比对完成，第二道闸门已经关闭，重新寻卡");
						}
					} else if (ss.indexOf("2A040F23") == 0) {
						if (DeviceEventListener.getInstance().isStartThread()) {
							// log.debug("第二道闸门超时关闭");							

							TicketVerifyScreen.getInstance().offerEvent(new ScreenElementModifyEvent(0,
									ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null)); // 恢复初始界面
							DeviceEventListener.getInstance().setDeviceReader(true); // 允许寻卡
							DeviceEventListener.getInstance().setDealDeviceEvent(true); // 允许处理新的事件
							
							if(!DeviceConfig.getInstance().isAllowOpenSecondDoor()){
								GatCtrlSenderBroker.getInstance(DeviceConfig.GAT_MQ_Verify_CLIENT)
								.sendDoorCmd("PITEventTopic", DeviceConfig.Close_SECONDDOOR_Jms);
								DeviceConfig.getInstance().setAllowOpenSecondDoor(true);
							}
							
							log.debug("人证比对完成，第二道闸门超时关闭，重新寻卡");
							
							
						}
					} else if (ss.indexOf("2A510123") == 0) {
						log.debug("点亮入口绿色箭头");
					} else if (ss.indexOf("2A510023") == 0) {
						log.debug("点亮入口红色叉叉");
					} else if (ss.indexOf("2A550123") == 0) {
						if (DeviceEventListener.getInstance().isStartThread()) {
							log.debug("旅客求助按钮被按下");
							EmerButtonEvent embEvent = new EmerButtonEvent();
							embEvent.setEventNo("01");
							embEvent.setEventTime(CalUtils.getStringDateHaomiao());
							EmerButtonTask.getInstance().offerEmerButtonEvent(embEvent);
						}
					} else if (ss.indexOf("2A560123") == 0) {
						if (DeviceEventListener.getInstance().isStartThread()) {
							log.debug("客运求助按钮被按下");
							EmerButtonEvent embEvent = new EmerButtonEvent();
							embEvent.setEventNo("02");
							embEvent.setEventTime(CalUtils.getStringDateHaomiao());
							EmerButtonTask.getInstance().offerEmerButtonEvent(embEvent);
						}
					} else if (ss.indexOf("2A58") == 0) {
						log.debug("通道当前状态为：" + ss);
					}
					readBuffer.delete(0, readBuffer.length());
				}

			} catch (IOException ex) {
				log.error(ex);
			}
			break;
		}
	}

	public static void main(String[] args) {
		SecondGateDevice gateDevice = SecondGateDevice.getInstance();
		try {
			gateDevice.connect(DeviceConfig.getInstance().getSecondGateCrtlPort());
			gateDevice.openEmerDoor();
			CommUtil.sleep(1000);
			gateDevice.openTheSecondDoor();
			// gateDevice.close();
			// System.exit(0);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
