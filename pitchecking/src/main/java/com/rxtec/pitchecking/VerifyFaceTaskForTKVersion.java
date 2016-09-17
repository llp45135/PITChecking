package com.rxtec.pitchecking;

import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.device.CAMDevice;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.device.SecondGateDevice;
import com.rxtec.pitchecking.domain.FailedFace;
import com.rxtec.pitchecking.event.IDCardReaderEvent;
import com.rxtec.pitchecking.event.IDeviceEvent;
import com.rxtec.pitchecking.event.ScreenElementModifyEvent;
import com.rxtec.pitchecking.mq.JmsSender;
import com.rxtec.pitchecking.mq.JmsSenderTask;
import com.rxtec.pitchecking.mqtt.MqttServerPaho;
import com.rxtec.pitchecking.net.PTVerifyEventResultPublisher;
import com.rxtec.pitchecking.picheckingservice.FaceCheckingService;
import com.rxtec.pitchecking.picheckingservice.FaceDetectionService;
import com.rxtec.pitchecking.picheckingservice.IFaceTrackService;
import com.rxtec.pitchecking.picheckingservice.PITData;
import com.rxtec.pitchecking.picheckingservice.PITVerifyData;
import com.rxtec.pitchecking.picheckingservice.realsense.RSFaceDetectionService;
import com.rxtec.pitchecking.task.RunningStatus;
import com.rxtec.pitchecking.utils.CommUtil;

/**
 * 铁科版人脸比对处理任务
 * 用于铁科版本主控闸机程序
 * 
 * @author ZhaoLin
 *
 */
public class VerifyFaceTaskForTKVersion implements IVerifyFaceTask{
	private Logger log = LoggerFactory.getLogger("DeviceEventListener");

	IDCardReaderEvent event;
	IFaceTrackService faceTrackService = null;
//	PTVerifyEventResultPublisher eventResultPublisher = PTVerifyEventResultPublisher.getInstance();
	MqttServerPaho mqttServerPaho = MqttServerPaho.getInstance();

	public VerifyFaceTaskForTKVersion() {
		if (Config.getInstance().getVideoType() == Config.RealSenseVideo)
			faceTrackService = RSFaceDetectionService.getInstance();
		else
			faceTrackService = FaceDetectionService.getInstance();
	}

	/**
	 * 
	 * @param idCard
	 * @param ticket
	 * @return
	 */
	public PITVerifyData beginCheckFace(IDCard idCard, Ticket ticket,int delaySeconds) {

		AudioPlayTask.getInstance().start(DeviceConfig.cameraFlag); // 调用语音“请平视摄像头”
		PITVerifyData fd = null;

		/**
		 * 小孩及老人的特殊处理
		 */
		if (idCard.getAge() <= Config.ByPassMinAge || idCard.getAge() >= Config.ByPassMaxAge) {
			fd = new PITVerifyData();
			fd.setIdCardImg(idCard.getCardImageBytes());
			fd.setFaceImg(idCard.getCardImageBytes());
			fd.setFrameImg(idCard.getCardImageBytes());
			fd.setVerifyResult(1);
			log.debug("老人或小孩：PITVerifyData==" + fd);
			CommUtil.sleep(2000);

			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceCheckPass.getValue(), null, null, fd));
			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.showFaceDefaultContent.getValue(), null, null, fd));

			//向闸机主控程序发布比对结果
//			eventResultPublisher.publishResult(fd);  //Aeron版本 比对结果发布
			mqttServerPaho.publishResult(fd); //MQTT版本 比对结果发布
			return fd;
		}

		//通知人脸检测线程开始人脸比对
		faceTrackService.beginCheckingFace(idCard, ticket);		

		long nowMils = Calendar.getInstance().getTimeInMillis();

		try {
			/**
			 * 阻塞等待人脸比对线程或独立进程完成人脸比对
			 * 此处设置了超时等待时间
			 */
			fd = FaceCheckingService.getInstance().pollPassFaceData(delaySeconds);   
		} catch (InterruptedException ex) {
			log.error("pollPassFaceData call", ex);
		} catch (Exception ex) {
			log.error("pollPassFaceData call", ex);
		}

		
		//如果返回结果为空，则代表人脸比对失败
		if (fd == null) {
			long usingTime = Calendar.getInstance().getTimeInMillis() - nowMils;
			log.debug("pollPassFaceData, using " + usingTime + " value = null");
			faceTrackService.stopCheckingFace();

			AudioPlayTask.getInstance().start(DeviceConfig.emerDoorFlag); // 调用应急门开启语音


//			// mq发送人脸
//			if (DeviceConfig.getInstance().getMqStartFlag() == 1) {
//				FailedFace failedFace = FaceCheckingService.getInstance().getFailedFace();
//				log.debug("验证失败,mq sender:" + failedFace);
//				if (failedFace != null) {
//					JmsSenderTask.getInstance().offerFailedFace(failedFace);
//					FaceCheckingService.getInstance().setFailedFace(null);
//				}
//			} else {
//				FaceCheckingService.getInstance().setFailedFace(null);
//			}

			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceCheckFailed.getValue(), null, null, fd));
			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.showFaceDefaultContent.getValue(), null, null, fd));


		} else {
			long usingTime = Calendar.getInstance().getTimeInMillis() - nowMils; 
			log.info("pollPassFaceData, using " + usingTime + " ms, value=" + fd.getVerifyResult());
			faceTrackService.stopCheckingFace();
			FaceCheckingService.getInstance().setFailedFace(null);


			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.ShowFaceCheckPass.getValue(), null, null, fd));
			FaceTrackingScreen.getInstance().offerEvent(
					new ScreenElementModifyEvent(1, ScreenCmdEnum.showFaceDefaultContent.getValue(), null, null, fd));
		}
		
		//向闸机主控程序发布比对结果
//		eventResultPublisher.publishResult(fd);  //Aeron版本 比对结果发布
		mqttServerPaho.publishResult(fd);  //MQTT版本 比对结果发布
		return fd;
	}

}
