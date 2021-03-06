package com.rxtec.pitchecking.mqtt;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttSimpleCallback;
import com.rxtec.pitchecking.AudioPlayTask;
import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.DeviceEventListener;
import com.rxtec.pitchecking.FaceTrackingScreen;
import com.rxtec.pitchecking.HighFaceTrackingScreen;
import com.rxtec.pitchecking.IDCard;
import com.rxtec.pitchecking.ScreenCmdEnum;
import com.rxtec.pitchecking.SingleFaceTrackingScreen;
import com.rxtec.pitchecking.Ticket;
import com.rxtec.pitchecking.TicketVerifyScreen;
import com.rxtec.pitchecking.device.AudioDevice;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.device.FirstGateDevice;
import com.rxtec.pitchecking.device.SecondGateDevice;
import com.rxtec.pitchecking.device.easen.EasenVerifyResult;
import com.rxtec.pitchecking.event.ScreenElementModifyEvent;
import com.rxtec.pitchecking.mbean.ProcessUtil;
import com.rxtec.pitchecking.mq.RemoteMonitorPublisher;
import com.rxtec.pitchecking.mq.police.PITInfoPolicePublisher;
import com.rxtec.pitchecking.mq.quickhigh.PITInfoQuickPublisher;
import com.rxtec.pitchecking.net.event.CAMOpenBean;
import com.rxtec.pitchecking.net.event.EventHandler;
import com.rxtec.pitchecking.net.event.GatCrtlBean;
import com.rxtec.pitchecking.net.event.CAMNotifyBean;
import com.rxtec.pitchecking.net.event.PIVerifyRequestBean;
import com.rxtec.pitchecking.net.event.ScreenDisplayBean;
import com.rxtec.pitchecking.picheckingservice.FaceCheckingService;
import com.rxtec.pitchecking.picheckingservice.FaceDetectionService;
import com.rxtec.pitchecking.picheckingservice.IFaceTrackService;
import com.rxtec.pitchecking.picheckingservice.PITVerifyData;
import com.rxtec.pitchecking.picheckingservice.realsense.RSFaceDetectionService;
import com.rxtec.pitchecking.service.ticketsystem.TicketReservationService;
import com.rxtec.pitchecking.task.SendPITEventTask;
import com.rxtec.pitchecking.utils.CommUtil;
import com.rxtec.pitchecking.utils.IDCardUtil;
import com.rxtec.pitchecking.utils.JsonUtils;
import com.rxtec.pitchecking.utils.BASE64Util;
import com.rxtec.pitchecking.utils.CalUtils;

/**
 * 本类用来监听本地PITEventTopic,同GAT_RXTa.dll通信，间接控制门模块、转发铁科消息至人工处置窗
 * 
 * @author ZhaoLin
 *
 */
public class GatCtrlReceiverBroker {
	// 连接参数
	Logger log = LoggerFactory.getLogger("GatCtrlReceiverBroker");
	Logger checkLog = LoggerFactory.getLogger("FaceCheckingStandaloneTask");
	private final static boolean CLEAN_START = true;
	private final static short KEEP_ALIVE = 30;// 低耗网络，但是又需要及时获取数据，心跳30s
	private String CLIENT_ID = "GCR";// 客户端标识
	private final static int[] QOS_VALUES = { 0 };// 对应主题的消息级别
	private final static String[] TOPICS = { "PITEventTopic" };
	private String[] unSubscribeTopics = { "PITInfoTopic" };
	// private final static String SEND_TOPIC = "sub_topic";
	private static GatCtrlReceiverBroker _instance;

	private MqttClient mqttClient;

	IFaceTrackService faceTrackService = null;

	GatCtrlSenderBroker gatCtrlSender = null;

	private String localIP = DeviceConfig.getInstance().getIpAddress();

	/**
	 * 返回实例对象
	 * 
	 * @return
	 */
	public static synchronized GatCtrlReceiverBroker getInstance(String pidname) {
		if (_instance == null)
			_instance = new GatCtrlReceiverBroker(pidname);
		return _instance;
	}

	private GatCtrlReceiverBroker(String pidname) {
		if (pidname.equals(DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())) {
			if (Config.getInstance().getFaceControlMode() == 2 && !FaceCheckingService.getInstance().isFrontCamera()) { // 由人脸比对进程处理
				if (Config.getInstance().getVideoType() == Config.RealSenseVideo)
					faceTrackService = RSFaceDetectionService.getInstance();
				else
					faceTrackService = FaceDetectionService.getInstance();
			}
		}

		gatCtrlSender = GatCtrlSenderBroker.getInstance(pidname);

		CLIENT_ID = CLIENT_ID + localIP + pidname;

		while (true) {
			try {
				if (mqttClient == null || !mqttClient.isConnected()) {
					this.connect();
				}
				break;
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				log.error("connect:", e);
				CommUtil.sleep(5000);
				continue;
			}

		}
	}

	private int reconnect() {
		int flag = -1;
		try {
			mqttClient.connect(CLIENT_ID, CLEAN_START, KEEP_ALIVE);
			mqttClient.subscribe(TOPICS, QOS_VALUES);// 订阅接收主题
			flag = 0;
		} catch (MqttException ex) {
			log.error("MqttReceiverBroker reconnect", ex);
			flag = -1;
		}
		return flag;
	}

	/**
	 * 重新连接服务
	 */
	private void connect() throws MqttException {
		log.debug("start connect to " + DeviceConfig.getInstance().getMQTT_CONN_STR() + "# MyClientID==" + this.CLIENT_ID);
		mqttClient = new MqttClient(DeviceConfig.getInstance().getMQTT_CONN_STR());

		SimpleCallbackHandler simpleCallbackHandler = new SimpleCallbackHandler();
		mqttClient.registerSimpleHandler(simpleCallbackHandler);// 注册接收消息方法
		mqttClient.connect(CLIENT_ID, CLEAN_START, KEEP_ALIVE);
		mqttClient.subscribe(TOPICS, QOS_VALUES);// 订阅接收主题
		mqttClient.unsubscribe(unSubscribeTopics);

		log.info("**" + this.CLIENT_ID + " 连接 " + DeviceConfig.getInstance().getMQTT_CONN_STR() + " 成功**");

		// /**
		// * 完成订阅后，可以增加心跳，保持网络通畅，也可以发布自己的消息
		// */
		// mqttClient.publish("keepalive", "keepalive".getBytes(),
		// QOS_VALUES[0], true);// 增加心跳，保持网络通畅
	}

	/**
	 * 发送消息
	 * 
	 * @param clientId
	 * @param messageId
	 */
	public void sendMessage(String clientId, String message) {
		try {

			log.debug("send message to " + clientId + ", message is " + message);
			// 发布自己的消息
			mqttClient.publish(clientId, message.getBytes(), 0, false);
		} catch (MqttException e) {
			log.error("GatCtrlReceiverBroker sendMessage", e);
		} catch (Exception e) {
			log.error("GatCtrlReceiverBroker sendMessage", e);
		}
	}

	/**
	 * 简单回调函数，处理server接收到的主题消息
	 * 
	 * @author Join
	 * 
	 */
	class SimpleCallbackHandler implements MqttSimpleCallback {

		/**
		 * 当客户机和broker意外断开时触发 可以再此处理重新订阅
		 */
		@Override
		public void connectionLost() throws Exception {
			// TODO Auto-generated method stub
			log.debug("客户机和broker已经断开");

			while (true) {
				log.debug("3s后开始尝试重新连接...");
				Thread.sleep(3000);
				int flag = reconnect();
				if (flag == 0) {
					log.debug("重新连接mq服务成功,退出重连循环!");
					break;
				}
			}
		}

		/**
		 * 客户端订阅消息后，该方法负责回调接收处理消息
		 */
		@Override
		public void publishArrived(String topicName, byte[] payload, int Qos, boolean retained) throws Exception {
			// TODO Auto-generated method stub
			// log.debug("订阅主题: " + topicName);
			// log.debug("消息数据: " + new String(payload));
			// log.debug("消息级别(0,1,2): " + Qos);
			// log.debug("是否是实时发送的消息(false=实时，true=服务器上保留的最后消息): " + retained);

			String mqttMessage = new String(payload);
			String orignMqttMessage = mqttMessage;
			// log.debug("orign mqttMessage==" + orignMqttMessage);

			try {
				String qrcode = "";
				if (topicName.equals("PITEventTopic")) {
					if (mqttMessage.toLowerCase().indexOf("eventsource") != -1) { // 收到门控制发回的指令
						mqttMessage = mqttMessage.replace("\r\n", "");
						mqttMessage = mqttMessage.replace(" ", "");
						mqttMessage = mqttMessage.toLowerCase();

						GatCrtlBean gatCrtlBean = new GatCrtlBean();
						if (mqttMessage.toLowerCase().indexOf("qrcode") != -1) {
							mqttMessage = mqttMessage.replace("{", "");
							mqttMessage = mqttMessage.replace("}", "");
							StringTokenizer st = new StringTokenizer(mqttMessage, ",");
							while (st.hasMoreTokens()) {
								String tt = st.nextToken();
								if (tt.indexOf("\"event\"") != -1) {
									String event = tt.substring(tt.indexOf(":") + 1).replace("\"", "");
									// log.debug("event==" + event);
									gatCrtlBean.setEvent(Integer.parseInt(event));
								} else if (tt.indexOf("\"target\"") != -1) {
									String target = tt.substring(tt.indexOf(":") + 1).replace("\"", "");
									// log.debug("target==" + target);
									gatCrtlBean.setTarget(target);
								} else if (tt.indexOf("\"eventsource\"") != -1) {
									String eventsource = tt.substring(tt.indexOf(":") + 1).replace("\"", "");
									// log.debug("eventsource==" + eventsource);
									gatCrtlBean.setEventsource(eventsource);
								} else if (tt.indexOf("qrcode") != -1) {
									qrcode = tt.substring(tt.indexOf(":") + 1).replace("\"", "");
									if (qrcode.length() > 144) {
										qrcode = qrcode.substring(0, 144);
									}
								}
							}
						} else {
							ObjectMapper mapper = new ObjectMapper();
							gatCrtlBean = mapper.readValue(mqttMessage, GatCrtlBean.class);
						}

						// log.debug("mqttMessage===" + mqttMessage);
						log.debug("EventSource==" + gatCrtlBean.getEventsource() + ",Event==" + gatCrtlBean.getEvent() + ",Target==" + gatCrtlBean.getTarget());

						if (gatCrtlBean.getEventsource().equals("manual")) { // 来自人工窗消息
							log.debug("来自人工窗消息:" + mqttMessage);
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 由独立比对进程处理
								if (Config.getInstance().getIsUseManualMQ() == 1) { // 是否连人工窗
									if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenSecondDoor) {
										AudioPlayTask.getInstance().start(DeviceConfig.AudioCheckSuccFlag); // 语音："验证成功，请通过"
									} else if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenThirdDoor) {
										AudioPlayTask.getInstance().start(DeviceConfig.AudioCheckFailedFlag); // 语音："验证失败，请从侧门离开通道"
									} else if (gatCrtlBean.getEvent() == 80004) { // 读二代证失败
										AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedIdCardFlag);
									} else if (gatCrtlBean.getEvent() == 80001 || gatCrtlBean.getEvent() == 90202) { // 读二维码失败/无电子票
										AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedQrcodeFlag);
									} else if (gatCrtlBean.getEvent() == 80002) { // 票证不符
										AudioPlayTask.getInstance().start(DeviceConfig.AudioValidIDandTicketFlag);
									} else if (gatCrtlBean.getEvent() == 51681 || gatCrtlBean.getEvent() == 90238) { // 已过进站时间
										AudioPlayTask.getInstance().start(DeviceConfig.AudioPassTimeFlag);
									} else if (gatCrtlBean.getEvent() == 51682 || gatCrtlBean.getEvent() == 90236) { // 未到进站时间
										AudioPlayTask.getInstance().start(DeviceConfig.AudioNeverTimeFlag);
									} else if (gatCrtlBean.getEvent() == 51605) { // 越站乘车
										AudioPlayTask.getInstance().start(DeviceConfig.AudioPassStationFlag);
									} else if (gatCrtlBean.getEvent() == 51666) { // 票不符
										AudioPlayTask.getInstance().start(DeviceConfig.AudioWrongStationFlag);
									}
								}
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Guide_CLIENT)) { // 由用户引导进程处理
								log.debug("UserGuide进程处理来自暂停和恢复的消息:" + mqttMessage);

								if (gatCrtlBean.getEvent() == DeviceConfig.Event_PauseService) { // 暂停服务
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowStopCheckFault.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == DeviceConfig.Event_ContinueService) { // 恢复服务
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
								}
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Verify_CLIENT)) {
								if (Config.getInstance().getIsUseGatDll() == 0) {
									log.debug("Verify进程处理来自暂停和恢复的消息:" + mqttMessage);
									if (gatCrtlBean.getEvent() == DeviceConfig.Event_PauseService) { // 暂停服务

										DeviceEventListener.getInstance().setDeviceReader(false); // 暂停寻卡
										DeviceEventListener.getInstance().setDealDeviceEvent(false); // 停止处理新的事件

										TicketVerifyScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowStopCheckFault.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == DeviceConfig.Event_ContinueService) { // 恢复服务

										DeviceEventListener.getInstance().setDeviceReader(true); // 允许寻卡
										DeviceEventListener.getInstance().setDealDeviceEvent(true); // 允许处理新的事件

										TicketVerifyScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
									}
								}
							}

						} else if (gatCrtlBean.getEventsource().equals("tk")) { // ******************************来自铁科主控端消息
							// log.debug("来自铁科主控端消息:" + mqttMessage);
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_PidProtect_CLIENT)) { // 进程保护
								if (gatCrtlBean.getEvent() == 10001) { // 后置摄像头启动成功
									Config.getInstance().setCameraWork(true);
									log.debug("是否允许恢复后置检脸进程==" + Config.getInstance().isRebackTrackFlag());
									log.debug("后置摄像头是否启动==" + Config.getInstance().isCameraWork());
								} else if (gatCrtlBean.getEvent() == 10004) { // 前置摄像头启动成功
									Config.getInstance().setFrontCameraWork(true);
									log.debug("是否允许恢复前置检脸进程==" + Config.getInstance().isRebackFrontTrackFlag());
									log.debug("前置摄像头是否启动==" + Config.getInstance().isFrontCameraWork());
								} else if (gatCrtlBean.getEvent() == 10005) { // JAVA版主控启动成功
									Config.getInstance().setMainCrtlWork(true);
									log.debug("是否允许恢复java主控进程==" + Config.getInstance().isAllowRebackMain());
									log.debug("java主控是否启动==" + Config.getInstance().isMainCrtlWork());
								} else if (gatCrtlBean.getEvent() == 10006) { // 独立比对启动成功
									Config.getInstance().setStandaloneCheckWork(true);
									log.debug("是否允许恢复独立比对进程==" + Config.getInstance().isAllowRebackStandalone());
									log.debug("独立比对是否启动==" + Config.getInstance().isStandaloneCheckWork());
								} else if (gatCrtlBean.getEvent() == 10002) { // 立刻关闭PC
									log.debug("收到关闭PC命令，立即执行!");

									if (Config.getInstance().getShutdownPCMode() == 3) {
										Runtime.getRuntime().exec("shutdown -s -t " + String.valueOf(Config.getInstance().getClosePCDelay()));
										log.debug("已发出" + Config.getInstance().getClosePCDelay() + "秒后关机命令!");
									}
									if (Config.getInstance().getShutdownPCMode() == 2) {
										Runtime.getRuntime().exec("shutdown -r -t " + String.valueOf(Config.getInstance().getClosePCDelay()));
										log.debug("已发出" + Config.getInstance().getClosePCDelay() + "秒后重启命令!");
									}
									if (Config.getInstance().getShutdownPCMode() == 1) {
										Runtime.getRuntime().exec("shutdown -l");
										log.debug("已发出注销命令!");
									}
								} else if (gatCrtlBean.getEvent() == 10010) { // 收到开始检脸指令
									// log.debug("收到开始检脸指令");
									checkLog.debug("----------------------");
									checkLog.debug("收到开始检脸指令");
									DeviceConfig.getInstance().setInTracking(true);
								} else if (gatCrtlBean.getEvent() == 10011 || gatCrtlBean.getEvent() == 10012) { // 10011:人脸核验成功--10012:人脸核验失败
									if (gatCrtlBean.getEvent() == 10011) {
										checkLog.debug("人脸核验成功");
										checkLog.debug("#############################");
									}
									if (gatCrtlBean.getEvent() == 10012) {
										checkLog.debug("人脸核验失败");
										checkLog.debug("#############################");
									}

									FaceCheckingService.getInstance().resetIdCard();
									DeviceConfig.getInstance().setInTracking(false);
								}
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 独立比对进程

								if (Config.getInstance().getIsUseManualMQ() == 1) { // 是否连人工窗
									// RemoteMonitorPublisher.getInstance().offerEventData(gatCrtlBean.getEvent());
									mqttMessage = mqttMessage.replace("127.0.0.1", localIP);
									orignMqttMessage = orignMqttMessage.replace("127.0.0.1", localIP);
									log.debug("来自铁科主控端消息:" + orignMqttMessage);

									if (gatCrtlBean.getEvent() == 10010) { // 收到开始检脸指令
										// log.debug("收到开始检脸指令");
										checkLog.debug("----------------------");
										checkLog.debug("收到开始检脸指令");
										DeviceConfig.getInstance().setInTracking(true);

										/**
										 * Set IDcard By易胜
										 */
										if (Config.getInstance().getFaceVerifyType().equals(Config.FaceVerifyEASEN)) { // 易胜sdk
											File easenIdcardZp = new File(Config.getInstance().getEasenConfigPath() + "/easenzp.jpg");
											BufferedImage easenIdcardZpBufferedImage = ImageIO.read(easenIdcardZp);
											int retval = FaceCheckingService.getInstance().getFaceVerify().setIDCardPhoto(easenIdcardZpBufferedImage);
											FaceCheckingService.getInstance().setIdCardPhotoRet(retval);
											GatCtrlSenderBroker.getInstance(DeviceConfig.GAT_MQ_Standalone_CLIENT).sendDoorCmd("PITEventTopic",
													DeviceConfig.Event_SetIDPhotoSuccess);
										} else {
											FaceCheckingService.getInstance().setIdCardPhotoRet(0);
										}

										AudioPlayTask.getInstance().start(DeviceConfig.AudioTakeCardFlag);
									} else if (gatCrtlBean.getEvent() == 10001) { // 后置摄像头启动成功
										// mqttSenderBroker.publishResult(fd,
										// Config.VerifyPassedStatus);
									} else if (gatCrtlBean.getEvent() == 10003) { // 停止送前置摄像头的人脸
										FaceCheckingService.getInstance().setSendFrontCameraFace(false);
										checkLog.debug("停止送前置摄像头的人脸");
										// FaceCheckingService.getInstance().resetFaceDataQueue();
									} else if (gatCrtlBean.getEvent() == 10007) { // 允许送前置摄像头的人脸
										FaceCheckingService.getInstance().clearLastIdCard();
										FaceCheckingService.getInstance().setDealNoneTrackFace(false);
										FaceCheckingService.getInstance().setSendFrontCameraFace(true);
										log.debug("允许送前置摄像头的人脸");
									} else if (gatCrtlBean.getEvent() == 10011 || gatCrtlBean.getEvent() == 10012) { // 10011:人脸核验成功--10012:人脸核验失败
										FaceCheckingService.getInstance().setSendFrontCameraFace(true);
										FaceCheckingService.getInstance().setReceiveBehindCameraFace(false); // 设置为还没有接收到后置人脸
										if (gatCrtlBean.getEvent() == 10011) {
											// log.debug("人脸核验成功");
											checkLog.debug("人脸核验成功");
											checkLog.debug("#############################");
										}
										if (gatCrtlBean.getEvent() == 10012) {
											// log.debug("人脸核验失败");
											checkLog.debug("人脸核验失败");
											checkLog.debug("#############################");
										}

										FaceCheckingService.getInstance().resetIdCard();
										FaceCheckingService.getInstance().setIdCardPhotoRet(-1);
										DeviceConfig.getInstance().setInTracking(false);
									} else if (gatCrtlBean.getEvent() == 80004) { // 读二代证失败
										AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedIdCardFlag);
									} else if (gatCrtlBean.getEvent() == 80001 || gatCrtlBean.getEvent() == 90202) { // 读二维码失败/无电子票
										AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedQrcodeFlag);
									} else if (gatCrtlBean.getEvent() == 80002) { // 票证不符
										AudioPlayTask.getInstance().start(DeviceConfig.AudioValidIDandTicketFlag);
									} else if (gatCrtlBean.getEvent() == 51681 || gatCrtlBean.getEvent() == 90238) { // 已过进站时间
										AudioPlayTask.getInstance().start(DeviceConfig.AudioPassTimeFlag);
									} else if (gatCrtlBean.getEvent() == 51682 || gatCrtlBean.getEvent() == 90236) { // 未到进站时间
										AudioPlayTask.getInstance().start(DeviceConfig.AudioNeverTimeFlag);
									} else if (gatCrtlBean.getEvent() == 51605) { // 越站乘车
										AudioPlayTask.getInstance().start(DeviceConfig.AudioPassStationFlag);
									} else if (gatCrtlBean.getEvent() == 51666) { // 票不符
										AudioPlayTask.getInstance().start(DeviceConfig.AudioWrongStationFlag);
									} else if (gatCrtlBean.getEvent() == 80003) { // 多人尾随
										AudioPlayTask.getInstance().start(DeviceConfig.AudioForbidenTrailFlag);
									} else if (gatCrtlBean.getEvent() == 80006) { // 重复刷票
										AudioPlayTask.getInstance().start(DeviceConfig.AudioRepeatCheckFlag);
									} else if (gatCrtlBean.getEvent() == 89001) { // 无电子船票
										AudioPlayTask.getInstance().start(DeviceConfig.AudioNoShipTicketFlag);
									} else if (gatCrtlBean.getEvent() == 89002) { // 船票不符
										AudioPlayTask.getInstance().start(DeviceConfig.AudioInvalidShipTicketFlag);
									}

									SendPITEventTask.getInstance().offerEventData(orignMqttMessage); // 塞进队列，准备发送至控制台

									// ManualEventSenderBroker.getInstance(DeviceConfig.GAT_MQ_Standalone_CLIENT)
									// .sendDoorCmd(mqttMessage);
								}
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())) { // 由检脸进程处理

								log.debug("检脸进程处理来自铁科主控端的消息:" + mqttMessage);
								/**
								 * 双门模式
								 */
								if (Config.getInstance().getDoorCountMode() == DeviceConfig.DOUBLEDOOR) { // 双门模式
									if (gatCrtlBean.getEvent() == 10010) { // 人脸开始核验
										if (Config.getInstance().getFaceControlMode() == 2 && !FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
											// 设置为处于人脸核验中
											DeviceConfig.getInstance().setAllowOpenSecondDoor(true);
											DeviceConfig.getInstance().setInTracking(true);

											DeviceConfig.getInstance().setFaceScreenDisplay("");

											Ticket ticket = new Ticket();
											IDCard idCard = FaceCheckingService.getInstance().getIdcard();
											faceTrackService.beginCheckingFace(idCard, ticket);

											if (Config.getInstance().getBackScreenMode() == 1) {
												FaceTrackingScreen.getInstance().offerEvent(
														new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowBeginCheckFaceContent.getValue(), null, null, null));
											}
											if (Config.getInstance().getBackScreenMode() == 2) {
												HighFaceTrackingScreen.getInstance().offerEvent(
														new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowBeginCheckFaceContent.getValue(), null, null, null));
											}
										}
									} else if (gatCrtlBean.getEvent() == 10003) { // 停止送前置摄像头的人脸
										FaceCheckingService.getInstance().setSendFrontCameraFace(false);
										log.debug("停止送前置摄像头的人脸");
									} else if (gatCrtlBean.getEvent() == 10007) { // 允许送前置摄像头的人脸
										FaceCheckingService.getInstance().clearLastIdCard();
										FaceCheckingService.getInstance().setDealNoneTrackFace(false);
										FaceCheckingService.getInstance().setSendFrontCameraFace(true);
										log.debug("允许送前置摄像头的人脸");
									} else if (gatCrtlBean.getEvent() == 10008) {
										FaceCheckingService.getInstance().setIdCardPhotoRet(0);
									} else if (gatCrtlBean.getEvent() == 10011 || gatCrtlBean.getEvent() == 10012) { // 检脸成功
										if (gatCrtlBean.getEvent() == 10011) {
											// log.debug("人脸核验成功");
											log.debug("人脸核验成功");
											log.debug("#############################");
											if (Config.getInstance().getFaceControlMode() == 2 && !FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
												FaceCheckingService.getInstance().setLastIdCard(FaceCheckingService.getInstance().getIdcard());
											}
										}
										if (gatCrtlBean.getEvent() == 10012) {
											// log.debug("人脸核验失败");
											log.debug("人脸核验失败");
											log.debug("#############################");
										}

										if (!FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
											if (Config.getInstance().getFaceControlMode() == 2)
												faceTrackService.stopCheckingFace();

											if (Config.getInstance().getBackScreenMode() == 1) {
												FaceTrackingScreen.getInstance()
														.offerEvent(new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceDisplayFromTK.getValue(), null, null, null));
											}
											if (Config.getInstance().getBackScreenMode() == 2) {
												HighFaceTrackingScreen.getInstance()
														.offerEvent(new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceDisplayFromTK.getValue(), null, null, null));
											}
										}

										// 清除身份证信息，不再让前置摄像头使用
										FaceCheckingService.getInstance().setSendFrontCameraFace(true); // 允许送前置摄像头人脸
										log.debug("允许送前置摄像头人脸");
										FaceCheckingService.getInstance().resetIdCard();
										FaceCheckingService.getInstance().setIdCardPhotoRet(-1);

										DeviceConfig.getInstance().setInTracking(false); // 设置人脸核验已经完成
										log.debug("清除身份证信息，不再让前置摄像头使用");
									}
								}

								/**
								 * 单门模式
								 */
								if (Config.getInstance().getDoorCountMode() == DeviceConfig.SINGLEDOOR) { // 单门模式
									if (gatCrtlBean.getEvent() == 10000) { // 设备启动成功
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 10001) { // 摄像头启动成功
										Config.getInstance().setCameraWork(true);
									} else if (gatCrtlBean.getEvent() == 10010) { // 开始检脸
										if (Config.getInstance().getFaceControlMode() == 2 && !FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
											// 设置为处于人脸核验中
											DeviceConfig.getInstance().setAllowOpenSecondDoor(true);
											DeviceConfig.getInstance().setInTracking(true);

											DeviceConfig.getInstance().setFaceScreenDisplay("");

											Ticket ticket = new Ticket();
											IDCard idCard = FaceCheckingService.getInstance().getIdcard();
											faceTrackService.beginCheckingFace(idCard, ticket);

											SingleFaceTrackingScreen.getInstance()
													.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketVerifySucc.getValue(), null, null, null));

											SingleFaceTrackingScreen.getInstance()
													.offerEvent(new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowBeginCheckFaceContent.getValue(), null, null, null));
										}

									} else if (gatCrtlBean.getEvent() == 10011 || gatCrtlBean.getEvent() == 10012) { // 检脸成功
										if (gatCrtlBean.getEvent() == 10011) {
											// log.debug("人脸核验成功");
											log.debug("人脸核验成功");
											log.debug("#############################");
											if (Config.getInstance().getFaceControlMode() == 2 && !FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
												FaceCheckingService.getInstance().setLastIdCard(FaceCheckingService.getInstance().getIdcard());
											}
										}
										if (gatCrtlBean.getEvent() == 10012) {
											// log.debug("人脸核验失败");
											log.debug("人脸核验失败");
											log.debug("#############################");
										}

										if (!FaceCheckingService.getInstance().isFrontCamera()) { // 统一由比对进程处理时才由此调用
											if (Config.getInstance().getFaceControlMode() == 2)
												faceTrackService.stopCheckingFace();

											SingleFaceTrackingScreen.getInstance()
													.offerEvent(new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceDisplayFromTK.getValue(), null, null, null));

											SingleFaceTrackingScreen.getInstance()
													.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
										}

										// 清除身份证信息，不再让前置摄像头使用
										FaceCheckingService.getInstance().setSendFrontCameraFace(true); // 允许送前置摄像头人脸
										log.debug("允许送前置摄像头人脸");
										FaceCheckingService.getInstance().resetIdCard();
										FaceCheckingService.getInstance().setIdCardPhotoRet(-1);

										DeviceConfig.getInstance().setInTracking(false); // 设置人脸核验已经完成
										log.debug("清除身份证信息，不再让前置摄像头使用");
									} else if (gatCrtlBean.getEvent() == 80006) { // 重复验票
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showRepeatCheck.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 80004) { // 读二代证失败
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showFailedIDCard.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 80001) { // 读二维码失败/无电子票
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showFailedQRCode.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 90202) { // 读二维码失败/无电子票
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showNoETicket.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 80002) { // 票证不符
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showInvalidTicketAndIDCard.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 51681) { // 已过进站时间
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showPassTime.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 90238) { // 电子票已过进站时间
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showETicketPassTime.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 51682) { // 未到进站时间
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showNotInTime.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 90236) { // 电子票未到进站时间
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showETicketNotInTime.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 51605) { // 越站乘车
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showPassStation.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == 51666) { // 票不符
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showWrongStation.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == ScreenCmdEnum.ShowQRDeviceException.getValue()) { // 二维码读卡器故障
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowQRDeviceException.getValue(), null, null, null));
									} else if (gatCrtlBean.getEvent() == ScreenCmdEnum.ShowIDDeviceException.getValue()) { // 二代证读卡器故障
										SingleFaceTrackingScreen.getInstance()
												.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowIDDeviceException.getValue(), null, null, null));
									}
								}
								/**
								 * 
								 */
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Verify_CLIENT)) { // 由Java主控进程处理
								if (gatCrtlBean.getEvent() == 10010) {
									DeviceConfig.getInstance().setInTracking(true);
								} else if (gatCrtlBean.getEvent() == 10011 || gatCrtlBean.getEvent() == 10012) { // 检脸成功或失败
									if (gatCrtlBean.getEvent() == 10011) { // 检脸成功
										IDCard succCard = FaceCheckingService.getInstance().getIdcard();
										DeviceEventListener.getInstance().getPersonPassMap().put(succCard.getIdNo(), succCard);

										Ticket succTicket = DeviceEventListener.getInstance().getTicketVerify().getTicket();
										log.info("succTicket==" + succTicket);
										if (succTicket != null) {
											DeviceEventListener.getInstance().getTicketPassMap().put(succTicket.getTicketNo(), succTicket);
											log.info("票号" + succTicket.getTicketNo() + "已经put进TicketPassMap");
										}
									}

									DeviceConfig.getInstance().setInTracking(false);

									DeviceEventListener.getInstance().resetTicketAndIDCard();
									DeviceEventListener.getInstance().clearDeviceEvent();
								} else if (gatCrtlBean.getEvent() == 10013) { // 核验超时
									DeviceConfig.getInstance().setInTracking(false);
								} else if (gatCrtlBean.getEvent() == 10014) { // 检测到低于1.4m身高的人脸
									int cc = DeviceEventListener.getInstance().getChildCheckCount();
									cc = cc + 1;
									log.info("检测到低于1.4m身高的人脸,次数==" + cc);
									DeviceEventListener.getInstance().setChildCheckCount(cc);
								}

							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Guide_CLIENT)) { // 由用户引导进程处理
								log.debug("UserGuide进程处理来自铁科主控端的消息:" + mqttMessage);

								if (gatCrtlBean.getEvent() == 10010) { // 开始检脸
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketVerifySucc.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 10011) { // 检脸成功
									CommUtil.sleep(3 * 1000);
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 10012) { // 检脸失败
									CommUtil.sleep(5 * 1000);
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 80004) { // 读二代证失败

									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketVerifyWaitInput.getValue(), new Ticket(), null, null));

									// TicketVerifyScreen.getInstance().offerEvent(new
									// ScreenElementModifyEvent(0,
									// ScreenCmdEnum.showFailedIDCard.getValue(),
									// null, null, null));
								} else if (gatCrtlBean.getEvent() == 80001) { // 读二维码失败/无电子票
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketVerifyWaitInput.getValue(), null, new IDCard(), null));

									// TicketVerifyScreen.getInstance().offerEvent(new
									// ScreenElementModifyEvent(0,
									// ScreenCmdEnum.showFailedQRCode.getValue(),
									// null, null, null));
								} else if (gatCrtlBean.getEvent() == 90202) { // 读二维码失败/无电子票
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketVerifyWaitInput.getValue(), null, new IDCard(), null));

									// TicketVerifyScreen.getInstance().offerEvent(new
									// ScreenElementModifyEvent(0,
									// ScreenCmdEnum.showNoETicket.getValue(),
									// null, null, null));
								} else if (gatCrtlBean.getEvent() == 80002) { // 票证不符
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showInvalidTicketAndIDCard.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 51681) { // 已过进站时间
									TicketVerifyScreen.getInstance().offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showPassTime.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 90238) { // 电子票已过进站时间
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showETicketPassTime.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 51682) { // 未到进站时间
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showNotInTime.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 90236) { // 电子票未到进站时间
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showETicketNotInTime.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 51605) { // 越站乘车
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showPassStation.getValue(), null, null, null));
								} else if (gatCrtlBean.getEvent() == 51666) { // 票不符
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.showWrongStation.getValue(), null, null, null));
								}
							}
						} else if (gatCrtlBean.getEventsource().equals("faceverifyback") || gatCrtlBean.getEventsource().equals("manualback")) {
							log.debug("来自DLL的回执:" + mqttMessage);
							// if (CLIENT_ID.equals("GCR" +
							// localIP
							// + DeviceConfig.GAT_MQ_Verify_CLIENT)) {
							// if (Config.getInstance().getIsUseGatDll() == 1) {
							// if (gatCrtlBean.getEvent() ==
							// DeviceConfig.Event_SecondDoorHasClosed) {
							// TicketVerifyScreen.getInstance().offerEvent(new
							// ScreenElementModifyEvent(0,
							// ScreenCmdEnum.ShowTicketDefault.getValue(), null,
							// null, null)); // 恢复初始界面
							// DeviceEventListener.getInstance().setDeviceReader(true);
							// // 允许寻卡
							// DeviceEventListener.getInstance().setDealDeviceEvent(true);
							// // 允许处理新的事件
							// log.debug("人证比对完成，第三道闸门已经关闭，重新寻卡");
							// }
							// }
							// }
						} else if (gatCrtlBean.getEventsource().equals("faceaudio")) {
							log.debug("来自检脸进程的语音事件消息:" + mqttMessage);
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 由独立比对进程处理

								if (gatCrtlBean.getEvent() == DeviceConfig.AudioCheckSuccFlag) {// 语音："验证成功，请通过"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioCheckSuccFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioRoomOneFlag) {// 语音："请到一候车室候车"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioRoomOneFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioRoomTwoFlag) {// 语音："请到一候车室候车"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioRoomTwoFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioRoomThreeFlag) {// 语音："请到一候车室候车"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioRoomThreeFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioRoomFourFlag) {// 语音："请到一候车室候车"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioRoomFourFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioCheckFailedFlag) {// 语音："验证失败，请从侧门离开通道"
									AudioPlayTask.getInstance().start(DeviceConfig.AudioCheckFailedFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioTakeTicketFlag) { // 语音：取走票证、走入通道
									AudioPlayTask.getInstance().start(DeviceConfig.AudioTakeCardFlag);
									// CommUtil.sleep((long) (2.2 * 1000));

									// AudioPlayTask.getInstance().start(DeviceConfig.AudioTrackFaceFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioUseHelpFlag) { // 引导帮助
									AudioPlayTask.getInstance().start(DeviceConfig.AudioUseHelpFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioFailedIdCardFlag) { // 读二代证失败
									AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedIdCardFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioFailedQrcodeFlag) { // 读二维码失败/无电子票
									AudioPlayTask.getInstance().start(DeviceConfig.AudioFailedQrcodeFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioValidIDandTicketFlag) { // 票证不符
									AudioPlayTask.getInstance().start(DeviceConfig.AudioValidIDandTicketFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioPassTimeFlag) { // 已过进站时间
									AudioPlayTask.getInstance().start(DeviceConfig.AudioPassTimeFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioNeverTimeFlag) { // 未到进站时间
									AudioPlayTask.getInstance().start(DeviceConfig.AudioNeverTimeFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioPassStationFlag) { // 越站乘车
									AudioPlayTask.getInstance().start(DeviceConfig.AudioPassStationFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioWrongStationFlag) { // 票不符
									AudioPlayTask.getInstance().start(DeviceConfig.AudioWrongStationFlag);
								} else if (gatCrtlBean.getEvent() == DeviceConfig.AudioRepeatCheckFlag) { // 票不符
									AudioPlayTask.getInstance().start(DeviceConfig.AudioRepeatCheckFlag);
								}
							}
						} else if (gatCrtlBean.getEventsource().equals("qrdevice")) { // 二维码消息
							// log.info("来自二维码消息:" + mqttMessage);
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 由独立比对进程处理
								log.info("解析得到二维码源码QRCode==" + qrcode);
								DeviceConfig.getInstance().setQrCode(qrcode);
							}
						} else if (gatCrtlBean.getEventsource().equals("faceverify")) {
							if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenFirstDoor) {
								if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())) {

								}
							}
						} else if (gatCrtlBean.getEventsource().equals("gate")) {
							log.info("来自开二门的按钮或遥控消息:" + mqttMessage);
							if (gatCrtlBean.getEvent() == 10022) { // Event_PressOpenSecondDoorButton
								String pidName = "";
								if (Config.getInstance().getFaceControlMode() == 1) {
									pidName = DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum();
								}
								if (Config.getInstance().getFaceControlMode() == 2) {
									pidName = DeviceConfig.GAT_MQ_Standalone_CLIENT;
								}

								if (Config.getInstance().getFaceControlMode() == 1 && !FaceCheckingService.getInstance().isFrontCamera()) { // 由后置摄像头进程处理
									if (CLIENT_ID.equals("GCR" + localIP + pidName)) {

										log.debug("isInTracking==" + DeviceConfig.getInstance().isInTracking());

										if (DeviceConfig.getInstance().isInTracking()) { // 如果处于人脸核验中，那么必须立即结束本次核验，视为核验成功
											PITVerifyData failedFd = FaceCheckingService.getInstance().pollFailedFaceData();
											if (failedFd != null) {
												log.info("人工判断该次核验成功，按按钮放行，本次核验中最好的一次比对结果==" + failedFd.getVerifyResult());
												// failedFd.setVerifyResult((float)
												// 0.63);
												FaceCheckingService.getInstance().offerPassFaceData(failedFd);
											} else {
												log.info("还没有检测到任何一张脸，手动生成一张脸返回，目的是快速结束本次检脸..==" + gatCrtlBean.getEvent());
												PITVerifyData manualFd = new PITVerifyData();

												manualFd.setVerifyResult(-1);
												manualFd.setIdNo(MqttSenderBroker.getInstance(pidName).getUuid());
												manualFd.setIdCardImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												manualFd.setFaceImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												manualFd.setFrameImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												try {
													manualFd.setIdCardImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
													manualFd.setFaceImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
													manualFd.setFrameImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
												} catch (Exception ex) {
													log.error("老人或小孩:", ex);
												}

												FaceCheckingService.getInstance().offerPassFaceData(manualFd);
											}

										} else { // 不处于人脸核验中,直接开门
											log.info("不处于人脸核验中,不处理该事件!");
										}
									}
								}
								if (Config.getInstance().getFaceControlMode() == 2) { // 由人脸比对进程处理
									if (CLIENT_ID.equals("GCR" + localIP + pidName)) {

										log.debug("isInTracking==" + DeviceConfig.getInstance().isInTracking());

										if (DeviceConfig.getInstance().isInTracking()) { // 如果处于人脸核验中，那么必须立即结束本次核验，视为核验成功
											PITVerifyData failedFd = FaceCheckingService.getInstance().pollFailedFaceData();
											if (failedFd != null) {
												log.info("人工判断该次核验成功，按按钮放行，本次核验中最好的一次比对结果==" + failedFd.getVerifyResult());

												FaceCheckingService.getInstance().offerPassFaceData(failedFd);
											} else {
												log.info("还没有检测到任何一张脸，手动生成一张脸返回，目的是快速结束本次检脸..==" + gatCrtlBean.getEvent());
												PITVerifyData manualFd = new PITVerifyData();

												manualFd.setVerifyResult(-1);
												manualFd.setIdNo(MqttSenderBroker.getInstance(pidName).getUuid());
												manualFd.setIdCardImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												manualFd.setFaceImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												manualFd.setFrameImg(MqttSenderBroker.getInstance(pidName).getIdcardBytes());
												try {
													manualFd.setIdCardImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
													manualFd.setFaceImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
													manualFd.setFrameImgByBase64(BASE64Util.encryptBASE64(MqttSenderBroker.getInstance(pidName).getIdcardBytes()));
												} catch (Exception ex) {
													log.error("老人或小孩:", ex);
												}

												FaceCheckingService.getInstance().offerPassFaceData(manualFd);
											}

										} else { // 不处于人脸核验中,直接开门
											log.info("不处于人脸核验中,不处理该事件!");
										}
									}
								}
							}
						} else {
							log.debug("该条消息暂时不做处理：" + mqttMessage);
						}

						if (gatCrtlBean.getEvent() == 10020) { // 第二道门关闭
							DeviceConfig.getInstance().setAllowOpenSecondDoor(true);
							log.debug("已收到第二道门的关门回执 by " + gatCrtlBean.getEventsource() + "!允许重新转发手动开第二道门指令");
							log.debug("是否允许手动开第二道门==" + DeviceConfig.getInstance().isAllowOpenSecondDoor());

							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Verify_CLIENT)) { // java版主控进程
								if (Config.getInstance().getIsUseGatDll() == 1) { // 调用门控Dll
									TicketVerifyScreen.getInstance()
											.offerEvent(new ScreenElementModifyEvent(0, ScreenCmdEnum.ShowTicketDefault.getValue(), null, null, null)); // 恢复初始界面
									DeviceEventListener.getInstance().setDeviceReader(true); // 允许寻卡
									DeviceEventListener.getInstance().setDealDeviceEvent(true); // 允许处理新的事件
									log.debug("人证比对完成，第三道闸门已经关闭，重新寻卡");
								}
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())) { // 人脸检测进程
								log.info("后门关闭则触发后置摄像头开始在非检脸状态采集人脸,event==" + gatCrtlBean.getEvent());
								FaceCheckingService.getInstance().setDealNoneTrackFace(true); // 后门关闭则触发后置摄像头开始在非检脸状态采集人脸
							} else if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 人脸比对进程
								log.info("后门关闭则触发后置摄像头开始在非检脸状态采集人脸,event==" + gatCrtlBean.getEvent());
								FaceCheckingService.getInstance().setDealNoneTrackFace(true); // 后门关闭则触发后置摄像头开始在非检脸状态采集人脸
							}
						}

						/**
						 * 用于门控进程通过mq控制开门
						 */
						if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Verify_CLIENT)) { // java版主控进程
							if (Config.getInstance().getDoorCountMode() == DeviceConfig.DOUBLEDOOR) {
								if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenFirstDoor) { // 开1门
									if (Config.getInstance().getIsUseGatDll() == 0) {
										DeviceEventListener.getInstance().setReceiveOpenFirstDoorCmd(false);
										// FirstGateDevice.getInstance().openFirstDoor();//
										// 打开第一道门
										for (int rc = 0; rc < 3; rc++) {
											if (!DeviceEventListener.getInstance().isReceiveOpenFirstDoorCmd()) {
												FirstGateDevice.getInstance().openFirstDoor();
											} else {
												break;
											}
											CommUtil.sleep(100);
										}
									}
								} else if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenThirdDoor) { // 开3门指令
									if (Config.getInstance().getIsUseGatDll() == 0) {
										SecondGateDevice.getInstance().openEmerDoor(); //
									}
								}
							}

							if (gatCrtlBean.getEvent() == DeviceConfig.Event_OpenSecondDoor) { // 开2门指令
								log.info("收到开2门指令");

								if (Config.getInstance().getIsUseGatDll() == 0) {
									DeviceConfig.getInstance().setAllowOpenSecondDoor(false);
									DeviceEventListener.getInstance().setReceiveOpenSecondDoorCmd(false);
									// SecondGateDevice.getInstance().openTheSecondDoor();
									// //
									for (int rc = 0; rc < 3; rc++) {
										if (!DeviceEventListener.getInstance().isReceiveOpenSecondDoorCmd()) {
											SecondGateDevice.getInstance().openTheSecondDoor();
										} else {
											break;
										}
										CommUtil.sleep(100);
									}
								}
							}
						}

					} else { // 收到非Event事件
						// log.debug("-----------收到非Event事件-----------");
						if (mqttMessage.indexOf("CAM_Notify") != -1) {
							log.info("--------GatCtrlReciver 收到CAM_Notify请求--------");
							FaceCheckingService.getInstance().clearFaceVerifyQueue(); // 清理掉已有的人脸

							ObjectMapper mapper = new ObjectMapper();
							CAMNotifyBean notifyEvent = mapper.readValue(mqttMessage, CAMNotifyBean.class);

							IDCard notifyIdCard = new IDCard();
							notifyIdCard.setIdNo(notifyEvent.getUuid());
							notifyIdCard.setAge(notifyEvent.getAge());
							notifyIdCard.setGender(notifyEvent.getGender());
							notifyIdCard.setPersonName(notifyEvent.getPersonName());
							notifyIdCard.setIDBirth(notifyEvent.getIdBirth());
							notifyIdCard.setIDNation(notifyEvent.getIdNation());
							notifyIdCard.setIDDwelling(notifyEvent.getIdDwelling());
							notifyIdCard.setIDEfficb(notifyEvent.getIdEfficb());
							notifyIdCard.setIDEffice(notifyEvent.getIdEffice());
							notifyIdCard.setIDIssue(notifyEvent.getIdIssue());
							notifyIdCard.setCardImageBytes(notifyEvent.getIdPhoto());

							FaceCheckingService.getInstance().setIdcard(notifyIdCard);

							/**
							 * 
							 */
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Verify_CLIENT)) { // 由JAVA主控进程处理
								if (DeviceEventListener.getInstance().getPersonPassMap().get(notifyIdCard.getIdNo()) == null) {
									DeviceEventListener.getInstance().getPersonPassMap().clear();
								}
							}
							/**
							 * 每次有人刷证，都把证件信息传送到公安处做比对
							 */
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT)) { // 由人脸比对进程处理
								PITVerifyData pitData = new PITVerifyData();
								pitData.setIdNo(notifyIdCard.getIdNo());
								pitData.setGender(notifyIdCard.getGender());
								pitData.setPersonName(notifyIdCard.getPersonName());
								pitData.setAge(notifyIdCard.getAge());
								pitData.setIdBirth(notifyIdCard.getIDBirth());
								pitData.setIdNation(notifyIdCard.getIDNation());
								pitData.setIdDwelling(notifyIdCard.getIDDwelling());
								pitData.setIdEfficb(notifyIdCard.getIDEfficb());
								pitData.setIdEffice(notifyIdCard.getIDEffice());
								pitData.setIdIssue(notifyIdCard.getIDIssue());

								pitData.setVerifyResult((float) 0.01);
								pitData.setPitStation(DeviceConfig.getInstance().getBelongStationCode());
								pitData.setPitDate(CalUtils.getStringDateShort2());
								pitData.setPitTime(CalUtils.getStringDate());
								pitData.setIdCardImg(notifyIdCard.getCardImageBytes());
								pitData.setFaceImg(notifyIdCard.getCardImageBytes());
								pitData.setFrameImg(notifyIdCard.getCardImageBytes());
								try {
									pitData.setIdCardImgByBase64(BASE64Util.encryptBASE64(notifyIdCard.getCardImageBytes()));
									pitData.setFaceImgByBase64(BASE64Util.encryptBASE64(notifyIdCard.getCardImageBytes()));
									pitData.setFrameImgByBase64(BASE64Util.encryptBASE64(notifyIdCard.getCardImageBytes()));
								} catch (Exception ex) {
									log.error("老人或小孩:", ex);
								}

								if (Config.getInstance().getIsUsePoliceMQ() == 1) { // 将人脸数据传输至公安处
									PITInfoPolicePublisher.getInstance().offerVerifyData(pitData);
									log.info("将人脸数据传输至公安处");
								}

								if (Config.getInstance().getIsUseThirdMQ() == 1) { // 将人脸数据传输至第三方
									PITInfoQuickPublisher.getInstance().offerVerifyData(pitData);
								}
							}
						} else if (mqttMessage.indexOf("CAM_ScreenDisplay") != -1) {
							log.info("--------GatCtrlReciver 收到CAM_ScreenDisplay--------");
							ObjectMapper mapper = new ObjectMapper();
							ScreenDisplayBean screenDisplayBean = mapper.readValue(mqttMessage, ScreenDisplayBean.class);
							int displayTimeout = screenDisplayBean.getTimeout(); // 从CAM_ScreenDisplay输出的结构体获取到屏幕超时时间
							String faceScreenDisplay = screenDisplayBean.getScreenDisplay();
							log.info("CAM_ScreenDisplay faceScreenDisplay==" + faceScreenDisplay);
							log.info("CAM_ScreenDisplay displayTimeout==" + displayTimeout);

							DeviceConfig.getInstance().setFaceScreenDisplay(faceScreenDisplay);
							DeviceConfig.getInstance().setFaceScreenDisplayTimeout(displayTimeout);
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Standalone_CLIENT) && Config.getInstance().getFaceControlMode() == 2) { // 人脸比对进程

								if (faceScreenDisplay.indexOf("通过") != -1 || faceScreenDisplay.indexOf("请到") != -1 || faceScreenDisplay.indexOf("succed") != -1) {
									// 语音："验证成功，请通过"
									if (faceScreenDisplay.indexOf("一候") != -1) {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioRoomOneFlag, "FaceAudio"));
									} else if (faceScreenDisplay.indexOf("二候") != -1) {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioRoomTwoFlag, "FaceAudio"));
									} else if (faceScreenDisplay.indexOf("三候") != -1) {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioRoomThreeFlag, "FaceAudio"));
									} else if (faceScreenDisplay.indexOf("四候") != -1) {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioRoomFourFlag, "FaceAudio"));
									} else if (faceScreenDisplay.indexOf("高速候车") != -1) {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioRoomCityFlag, "FaceAudio"));
									} else {
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioCheckSuccFlag, "FaceAudio"));
									}
									// 通知人工控制台
									gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, DeviceConfig.Event_VerifyFaceSucc);
								} else {
									// 语音："验证失败，请从侧门离开通道"
									gatCtrlSender.sendDoorCmd(ProcessUtil.createTkEventJson(DeviceConfig.AudioCheckFailedFlag, "FaceAudio"));
									// 通知人工控制台
									gatCtrlSender.sendMessage(DeviceConfig.EventTopic, DeviceConfig.Event_VerifyFaceFailed);
								}
							}

							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())) { // 由检脸进程处理
								if (Config.getInstance().getFaceControlMode() == 1 && !FaceCheckingService.getInstance().isFrontCamera()) { // 由后置检脸进程处理由此调用
									if (faceScreenDisplay.indexOf("通过") != -1) {
										// 语音："验证成功，请通过"
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, ProcessUtil.createTkEventJson(DeviceConfig.AudioCheckSuccFlag, "FaceAudio"));
										// 通知人工控制台
										gatCtrlSender.sendDoorCmd(DeviceConfig.EventTopic, DeviceConfig.Event_VerifyFaceSucc);
									} else {
										// 语音："验证失败，请从侧门离开通道"
										gatCtrlSender.sendDoorCmd(ProcessUtil.createTkEventJson(DeviceConfig.AudioCheckFailedFlag, "FaceAudio"));
										// 通知人工控制台
										gatCtrlSender.sendMessage(DeviceConfig.EventTopic, DeviceConfig.Event_VerifyFaceFailed);
									}
								}
							}

						} else if (mqttMessage.indexOf("Heart") != -1) {
							if (CLIENT_ID.equals("GCR" + localIP + DeviceConfig.GAT_MQ_PidProtect_CLIENT)) {
								// log.debug("--------GatCtrlReciver
								// 收到Heart心跳--" + mqttMessage);
								String heartType = mqttMessage.substring(6, 7);
								if (heartType.equals("B")) {
									DeviceConfig.getInstance().setHeartBStr(mqttMessage.substring(8));
								} else if (heartType.equals("F")) {
									DeviceConfig.getInstance().setHeartFStr(mqttMessage.substring(8));
								} else if (heartType.equals("A")) {
									DeviceConfig.getInstance().setHeartAStr(mqttMessage.substring(8));
								} else if (heartType.equals("V")) {
									DeviceConfig.getInstance().setHeartVStr(mqttMessage.substring(8));
								}
							}
						}
					}
				}
			} catch (Exception ex) {
				log.error("GatCtrlReceiverBroker:", ex);
			}

			payload = null;
		}

	}

	public static void main(String[] args) {
		// GatCtrlReceiverBroker mqttBroker =
		// GatCtrlReceiverBroker.getInstance("Verify");
		// GatCtrlReceiverBroker mqttBroker =
		// GatCtrlReceiverBroker.getInstance("Track");
		GatCtrlReceiverBroker mqttBroker = GatCtrlReceiverBroker.getInstance(DeviceConfig.GAT_MQ_Standalone_CLIENT);// 启动PITEventTopic本地监听
		System.out.println("$$");

	}
}
