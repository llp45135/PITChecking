package com.rxtec.pitchecking.picheckingservice;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.IDCard;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.domain.FailedFace;
import com.rxtec.pitchecking.mq.police.PITInfoPolicePublisher;
import com.rxtec.pitchecking.net.PIVerifyResultSubscriber;
import com.rxtec.pitchecking.net.PTVerifyPublisher;
import com.rxtec.pitchecking.task.AutoLogonJob;
import com.rxtec.pitchecking.task.LuminanceListenerTask;
import com.rxtec.pitchecking.task.QueryUPSStatusTask;
import com.rxtec.pitchecking.task.ReadLightLevelTask;
import com.rxtec.pitchecking.task.RunSetCameraPropJob;

public class FaceCheckingService {
	private Logger log = LoggerFactory.getLogger("FaceCheckingService");

	private boolean isCheck = true;

	private static FaceCheckingService _instance = new FaceCheckingService();

	private FaceVerifyInterface faceVerify = null;


	// 已经检测人脸质量，待验证的队列
	private LinkedBlockingQueue<PITData> detectedFaceDataQueue;

	// 待验证的队列,独立进程比对
	private LinkedBlockingQueue<PITVerifyData> faceVerifyDataQueue;

	// 比对验证通过的队列
	private LinkedBlockingQueue<PITVerifyData> passFaceDataQueue;

	// 比对未通过验证的List，按照分值排序，分值高的在前
	private List<PITVerifyData> failedFaceDataList;

	private FailedFace failedFace = null;

	private boolean isSubscribeVerifyResult = true; // 是否订阅比对后的人脸结果
	private boolean isFrontCamera = false; // 是否前置摄像头
	private int idCardPhotoRet = -1;
	private IDCard idcard;
	
	public IDCard getIdcard() {
		return idcard;
	}

	public void setIdcard(IDCard idcard) {
		this.idcard = idcard;
	}

	public int getIdCardPhotoRet() {
		return idCardPhotoRet;
	}

	public void setIdCardPhotoRet(int idCardPhotoRet) {
		this.idCardPhotoRet = idCardPhotoRet;
	}

	public boolean isFrontCamera() {
		return isFrontCamera;
	}

	public void setFrontCamera(boolean isFrontCamera) {
		this.isFrontCamera = isFrontCamera;
	}

	public boolean isSubscribeVerifyResult() {
		return isSubscribeVerifyResult;
	}

	public void setSubscribeVerifyResult(boolean isSubscribeVerifyResult) {
		this.isSubscribeVerifyResult = isSubscribeVerifyResult;
	}

	public FailedFace getFailedFace() {
		return failedFace;
	}

	public void setFailedFace(FailedFace failedFace) {
		this.failedFace = failedFace;
	}

	public LinkedBlockingQueue<PITVerifyData> getPassFaceDataQueue() {
		return passFaceDataQueue;
	}

	private FaceCheckingService() {
		detectedFaceDataQueue = new LinkedBlockingQueue<PITData>(Config.getInstance().getDetectededFaceQueueLen());
		faceVerifyDataQueue = new LinkedBlockingQueue<PITVerifyData>(Config.getInstance().getDetectededFaceQueueLen());
		passFaceDataQueue = new LinkedBlockingQueue<PITVerifyData>(1);
		failedFaceDataList = new LinkedList<PITVerifyData>();
	}

	public static synchronized FaceCheckingService getInstance() {
		if (_instance == null)
			_instance = new FaceCheckingService();
		return _instance;
	}

	public void setFaceVerify(FaceVerifyInterface faceVerify) {
		this.faceVerify = faceVerify;
	}

	public FaceVerifyInterface getFaceVerify() {
		return faceVerify;
	}

	// 从阻塞队列中返回比对成功的人脸
	public PITVerifyData pollPassFaceData(int delaySeconds) throws InterruptedException {
		PITVerifyData fd = passFaceDataQueue.poll(delaySeconds, TimeUnit.SECONDS);
		return fd;
	}

	// 返回人脸比对分值最高的,比对未通过的人脸数据
	public PITVerifyData pollFailedFaceData() {
		if (failedFaceDataList.size() > 0)
			return failedFaceDataList.get(0);
		return null;
	}

	public PITData takeDetectedFaceData() throws InterruptedException {
		PITData p = detectedFaceDataQueue.take();
		log.debug("detectedFaceDataQueue length=" + detectedFaceDataQueue.size());

		return p;
	}

	/**
	 * 从待验证人脸队列中取人脸对象
	 * 
	 * @return
	 * @throws InterruptedException
	 */
	public PITVerifyData takeFaceVerifyData() throws InterruptedException {
		PITVerifyData v = faceVerifyDataQueue.take();
		log.debug("detectedFaceDataQueue length=" + detectedFaceDataQueue.size());
		return v;
	}

	// Offer 比对成功的人脸到阻塞队列
	public void offerPassFaceData(PITVerifyData fd) {
		isCheck = passFaceDataQueue.offer(fd);
		log.debug("offerPassFaceData...... " + isCheck);
	}

	// 添加比对失败的人脸到队列，按照比对比对结果分值排序
	public void offerFailedFaceData(PITVerifyData fd) {
		log.debug("offerFailedFaceData list length= " + failedFaceDataList.size());
		failedFaceDataList.add(fd);
		Collections.sort(failedFaceDataList);
	}

	/**
	 * 将人脸插入待检队列 人脸检测进程使用，检测到人脸即调用
	 * 
	 * @param faceData
	 */
	public void offerDetectedFaceData(PITData faceData) {
		if (!detectedFaceDataQueue.offer(faceData)) {
			detectedFaceDataQueue.poll();
			detectedFaceDataQueue.offer(faceData);
		}

		PITVerifyData vd = new PITVerifyData(faceData);

		if (!this.isFrontCamera) { // 后置摄像头
			if (vd.getIdCardImg() != null && vd.getFrameImg() != null && vd.getFaceImg() != null) {

				if (!faceVerifyDataQueue.offer(vd)) {
					faceVerifyDataQueue.poll();
					faceVerifyDataQueue.offer(vd);
				}
			} else {
				log.error("后置摄像头：offerDetectedFaceData 输入数据不完整！ vd.getIdCardImg()=" + vd.getIdCardImg()
						+ " vd.getFrameImg()=" + vd.getFrameImg() + " vd.getFaceImg()=" + vd.getFaceImg());
			}
		} else { // 前置摄像头
			if (vd.getFrameImg() != null && vd.getFaceImg() != null) {

				if (!faceVerifyDataQueue.offer(vd)) {
					faceVerifyDataQueue.poll();
					faceVerifyDataQueue.offer(vd);
				}
			} else {
				log.error("前置摄像头：offerDetectedFaceData 输入数据不完整！ vd.getIdCardImg()=" + vd.getIdCardImg()
						+ " vd.getFrameImg()=" + vd.getFrameImg() + " vd.getFaceImg()=" + vd.getFaceImg());
			}
		}

	}

	/**
	 * 将人脸数据插入待验证队列 人脸比对进程中，由aeron订阅端使用
	 * 
	 * @param faceData
	 */
	public void offerFaceVerifyData(PITVerifyData faceData) {
		if (!faceVerifyDataQueue.offer(faceData)) {
			faceVerifyDataQueue.poll();
			faceVerifyDataQueue.offer(faceData);
		}

	}

	/**
	 * 
	 * @param faceDatas
	 */
	public void offerDetectedFaceData(List<PITData> faceDatas) {
		detectedFaceDataQueue.clear();
		detectedFaceDataQueue.addAll(faceDatas);
	}

	public void resetFaceDataQueue() {
		detectedFaceDataQueue.clear();
		faceVerifyDataQueue.clear();
		passFaceDataQueue.clear();
		failedFaceDataList.clear();
	}

	ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * 启动人脸比对 本函数由独立人脸检测进程执行
	 */
	public void beginFaceCheckerTask() {
		/**
		 * 启动独立人脸比对进程的结果订阅
		 */
		if (this.isSubscribeVerifyResult) {
			PIVerifyResultSubscriber.getInstance().startSubscribing();
		} else {
			log.info("前置摄像头模式不订阅验证结果，不处理该逻辑");
		}
		/**
		 * 启动向人脸比对进程发布待验证人脸的发布者
		 */
		PTVerifyPublisher.getInstance();
	}

	/**
	 * 单独比对人脸进程Task，通过共享内存通信 此函数由独立人脸比对进程执行
	 */
	public void beginFaceCheckerStandaloneTask() {
		ExecutorService executer = Executors.newCachedThreadPool();
		FaceCheckingStandaloneTask task1 = new FaceCheckingStandaloneTask();
		executer.execute(task1);
		if (Config.getInstance().getFaceVerifyThreads() == 2) {
			FaceCheckingStandaloneTask task2 = new FaceCheckingStandaloneTask();
			executer.execute(task2);
		}
		log.info(".............Start " + Config.getInstance().getFaceVerifyThreads() + " FaceVerifyThreads");
		log.info("softVersion==" + DeviceConfig.softVersion);
		executer.shutdown();
	}

	/**
	 * 人脸比对进程使用
	 * 
	 * @return
	 */
	public int addQueryUPSJob() {
		int retVal = 1;
		try {
			SchedulerFactory sf = new StdSchedulerFactory();
			Scheduler sched = sf.getScheduler(); // 初始化调度器

			if (Config.getInstance().getIsUseUPSDevice() == 1) {
				JobDetail job1 = JobBuilder.newJob(QueryUPSStatusTask.class)
						.withIdentity("QueryUPSStatusJob", "queryUPSGroup").build();
				CronTrigger trigger1 = (CronTrigger) TriggerBuilder.newTrigger()
						.withIdentity("QueryUPSStatusTrigger", "queryUPSGroup")
						.withSchedule(CronScheduleBuilder.cronSchedule(Config.getInstance().getQueryUPSCronStr()))
						.build(); // 设置触发器

				Date ft1 = sched.scheduleJob(job1, trigger1); // 设置调度作业
				log.info(job1.getKey() + " has been scheduled to run at: " + ft1 + " and repeat based on expression: "
						+ trigger1.getCronExpression());
			}

			sched.start(); // 开启调度任务，执行作业

		} catch (Exception ex) {
			retVal = 0;
			ex.printStackTrace();
		}
		return retVal;
	}

	/**
	 * 定时启动任务 检脸进程使用
	 */
	public int addQuartzJobs() {
		int retVal = 1;
		try {
			SchedulerFactory sf = new StdSchedulerFactory();
			Scheduler sched = sf.getScheduler(); // 初始化调度器

			JobDetail job1 = JobBuilder.newJob(RunSetCameraPropJob.class)
					.withIdentity("setCameraPropJob1", "pitcheckGroup").build();
			CronTrigger trigger1 = (CronTrigger) TriggerBuilder.newTrigger()
					.withIdentity("setCameraPropTrigger1", "pitcheckGroup")
					.withSchedule(CronScheduleBuilder.cronSchedule(Config.getInstance().getInitCronStr())).build(); // 设置触发器
			Date ft1 = sched.scheduleJob(job1, trigger1); // 设置调度作业
			log.info(job1.getKey() + " has been scheduled to run at: " + ft1 + " and repeat based on expression: "
					+ trigger1.getCronExpression());

			JobDetail job2 = JobBuilder.newJob(RunSetCameraPropJob.class)
					.withIdentity("setCameraPropJob2", "pitcheckGroup").build();
			CronTrigger trigger2 = (CronTrigger) TriggerBuilder.newTrigger()
					.withIdentity("setCameraPropTrigger2", "pitcheckGroup")
					.withSchedule(CronScheduleBuilder.cronSchedule(Config.getInstance().getNightCronStr())).build(); // 设置触发器
			Date ft2 = sched.scheduleJob(job2, trigger2); // 设置调度作业
			log.info(job2.getKey() + " has been scheduled to run at: " + ft2 + " and repeat based on expression: "
					+ trigger2.getCronExpression());

			if (Config.getInstance().getIsUseLightLevelDevice() == 1) { // 是否使用光照监测器
				JobDetail job3 = JobBuilder.newJob(ReadLightLevelTask.class)
						.withIdentity("readLightLevelJob", "pitcheckGroup").build();
				CronTrigger trigger3 = (CronTrigger) TriggerBuilder.newTrigger()
						.withIdentity("readLightLevelTrigger", "pitcheckGroup")
						.withSchedule(CronScheduleBuilder.cronSchedule(Config.getInstance().getReadLightLevelCronStr()))
						.build(); // 设置触发器

				Date ft3 = sched.scheduleJob(job3, trigger3); // 设置调度作业
				log.info(job3.getKey() + " has been scheduled to run at: " + ft3 + " and repeat based on expression: "
						+ trigger3.getCronExpression());
			}

			if (Config.getInstance().getIsUseLuminanceListener() == 1) { // 是否监测照片的luminance
				JobDetail job4 = JobBuilder.newJob(LuminanceListenerTask.class)
						.withIdentity("readLuminanceJob", "pitcheckGroup").build();
				CronTrigger trigger4 = (CronTrigger) TriggerBuilder.newTrigger()
						.withIdentity("readreadLuminanceTrigger", "pitcheckGroup")
						.withSchedule(CronScheduleBuilder.cronSchedule(Config.getInstance().getQueryLuminanceCronStr()))
						.build(); // 设置触发器

				Date ft4 = sched.scheduleJob(job4, trigger4); // 设置调度作业
				log.info(job4.getKey() + " has been scheduled to run at: " + ft4 + " and repeat based on expression: "
						+ trigger4.getCronExpression());
			}

			sched.start(); // 开启调度任务，执行作业
		} catch (Exception ex) {
			retVal = 0;
			ex.printStackTrace();
		}
		return retVal;
	}

}
