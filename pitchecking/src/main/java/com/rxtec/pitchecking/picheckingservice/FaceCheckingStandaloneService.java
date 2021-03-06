package com.rxtec.pitchecking.picheckingservice;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.db.PitRecordLoger;
import com.rxtec.pitchecking.mbean.PITProcessDetect;
import com.rxtec.pitchecking.net.PIVerifySubscriber;
import com.rxtec.pitchecking.utils.CommUtil;

/**
 * 单独比对人脸进程
 * 
 * @author ZhaoLin
 *
 */
public class FaceCheckingStandaloneService {

	public static void main(String[] args) {
		//启动检脸进程保护的线程
		ExecutorService executer = Executors.newSingleThreadExecutor();
		PITProcessDetect pitProcessDetect = new PITProcessDetect();
		executer.execute(pitProcessDetect);

//		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//		FaceVerifyServiceManager mbean = new FaceVerifyServiceManager();
//		try {
//			server.registerMBean(mbean, new ObjectName("PITCheck:type=FaceCheckingService,name=FaceCheckingService"));
//		} catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException
//				| MalformedObjectNameException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		//是否启用monodb保存人脸数据
		if (Config.getInstance().getIsUseMongoDB() == 1) {
			PitRecordLoger.getInstance().clearExpirationData();
			PitRecordLoger.getInstance().startThread();
		}

		FaceCheckingService.getInstance().beginFaceCheckerStandaloneTask();
		PIVerifySubscriber s = new PIVerifySubscriber();

	}
	

}
