package com.rxtec.pitchecking.picheckingservice;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.domain.FailedFace;
import com.rxtec.pitchecking.net.PIVerifyResultSubscriber;
import com.rxtec.pitchecking.net.PTVerifyPublisher;

public class FaceCheckingService {
	private Logger log = LoggerFactory.getLogger("FaceCheckingService");

	private boolean isCheck = true;

	private static FaceCheckingService _instance = new FaceCheckingService();

	// 已经检测人脸质量，待验证的队列
	private LinkedBlockingQueue<PITData> detectedFaceDataQueue;

	// 待验证的队列,独立进程比对
	private LinkedBlockingQueue<FaceVerifyData> faceVerifyDataQueue ;

	// 比对验证通过的队列
	private LinkedBlockingQueue<PITData> passFaceDataQueue;

	private FailedFace failedFace;

	public FailedFace getFailedFace() {
		return failedFace;
	}

	public void setFailedFace(FailedFace failedFace) {
		this.failedFace = failedFace;
	}

	public LinkedBlockingQueue<PITData> getPassFaceDataQueue() {
		return passFaceDataQueue;
	}

	private FaceCheckingService() {
		detectedFaceDataQueue = new LinkedBlockingQueue<PITData>(Config.getInstance().getDetectededFaceQueueLen());
		faceVerifyDataQueue = new LinkedBlockingQueue<FaceVerifyData>(Config.getInstance().getDetectededFaceQueueLen());
		passFaceDataQueue = new LinkedBlockingQueue<PITData>(1);
	}

	public static synchronized FaceCheckingService getInstance() {
		if (_instance == null)
			_instance = new FaceCheckingService();
		return _instance;
	}

	public PITData pollPassFaceData() throws InterruptedException {
		PITData fd = passFaceDataQueue.poll(Config.getInstance().getFaceCheckDelayTime(), TimeUnit.SECONDS);
		return fd;
	}

	public PITData takeDetectedFaceData() throws InterruptedException {
		PITData p = detectedFaceDataQueue.take();
		log.debug("detectedFaceDataQueue length=" + detectedFaceDataQueue.size());

		return p;
	}

	public FaceVerifyData takeFaceVerifyData() throws InterruptedException {
		FaceVerifyData v = faceVerifyDataQueue.take();
		log.debug("detectedFaceDataQueue length=" + detectedFaceDataQueue.size());

		return v;
	}

	public void offerPassFaceData(PITData fd) {
		isCheck = passFaceDataQueue.offer(fd);
		log.debug("offerPassFaceData...... " + isCheck);
	}

	public void offerDetectedFaceData(PITData faceData) {
		if (!detectedFaceDataQueue.offer(faceData)) {
			detectedFaceDataQueue.poll();
			detectedFaceDataQueue.offer(faceData);
		}

		FaceVerifyData vd = new FaceVerifyData(faceData);

		if (!faceVerifyDataQueue.offer(vd)) {
			faceVerifyDataQueue.poll();
			faceVerifyDataQueue.offer(vd);
		}

		
		
	}
	
	
	public void offerFaceVerifyData(FaceVerifyData faceData) {
		if (!faceVerifyDataQueue.offer(faceData)) {
			faceVerifyDataQueue.poll();
			faceVerifyDataQueue.offer(faceData);
		}

	}
	
	

	public void offerDetectedFaceData(List<PITData> faceDatas) {
		detectedFaceDataQueue.clear();
		detectedFaceDataQueue.addAll(faceDatas);
	}

	public void resetFaceDataQueue() {
		detectedFaceDataQueue.clear();
		faceVerifyDataQueue.clear();
		passFaceDataQueue.clear();
	}

	ExecutorService executor = Executors.newCachedThreadPool();

	public void beginFaceCheckerTask() {
//		ExecutorService executer = Executors.newCachedThreadPool();
//		FaceCheckingTask task1 = new FaceCheckingTask(Config.FaceVerifyDLLName);
//		executer.execute(task1);
//
//		FaceCheckingTask task2 = new FaceCheckingTask(Config.FaceVerifyCloneDLLName);
//		executer.execute(task2);
		
		PIVerifyResultSubscriber.getInstance().startSubscribing();
		PTVerifyPublisher.getInstance();

	}
	
	
	public void beginFaceCheckerStandaloneTask() {
		ExecutorService executer = Executors.newCachedThreadPool();
		FaceCheckingStandaloneTask task1 = new FaceCheckingStandaloneTask(Config.FaceVerifyDLLName);
		executer.execute(task1);
		if(Config.getInstance().getFaceVerifyThreads() == 2){
			FaceCheckingStandaloneTask task2 = new FaceCheckingStandaloneTask(Config.FaceVerifyCloneDLLName);
			executer.execute(task2);
		}
		log.info(".............Start FaceVerifyThreads" + Config.getInstance().getFaceVerifyThreads());
		executer.shutdown();
	}

}
