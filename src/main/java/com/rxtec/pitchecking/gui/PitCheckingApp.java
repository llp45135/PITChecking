package com.rxtec.pitchecking.gui;

import org.openimaj.video.capture.VideoCaptureException;

import com.rxtec.pitchecking.device.DeviceEventListener;
import com.rxtec.pitchecking.device.TicketCheckScreen;
import com.rxtec.pitchecking.device.event.ScreenElementModifyEvent;
import com.rxtec.pitchecking.picheckingservice.FaceCheckingService;
import com.rxtec.pitchecking.picheckingservice.FaceData;
import com.rxtec.pitchecking.picheckingservice.FaceDetectionService;

/**
 * 人证识别自动验票软件主入口
 * @author ZhaoLin
 *
 */
public class PitCheckingApp {

	static DeviceEventListener eventListener = DeviceEventListener.getInstance();
	static TicketCheckScreen ticketCheckScreen = TicketCheckScreen.getInstance();

	public static void main(String[] args) throws InterruptedException {
		
		
		// TODO Auto-generated method stub
		//启动事件监听
		try {
			
			FaceDetectionService.getInstance().setVideoPanel(ticketCheckScreen.getVideoPanel());
			FaceDetectionService.getInstance().beginVideoCaptureAndTracking();
			FaceCheckingService.getInstance().beginFaceCheckerTask();
			
			eventListener.startListenEvent();


		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (VideoCaptureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		while(true){
			FaceData fd = FaceCheckingService.getInstance().getCheckedFaceData();
			if(fd != null){
				ScreenElementModifyEvent e = new ScreenElementModifyEvent(1,2,1);
				e.setFaceData(fd);
				TicketCheckScreen.getInstance().offerEvent(e);
				if(fd.getFaceCheckResult()>=0.7) FaceDetectionService.getInstance().stopCheckingFace();
			}
			
			Thread.sleep(50);
			
		}		
	}

}
