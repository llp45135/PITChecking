package com.rxtec.pitchecking.picheckingservice;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.db.MongoDB;
import com.rxtec.pitchecking.utils.CommUtil;

public class FaceImageLog {

	public static void clearFaceLogs() {
		int days = Config.getInstance().getFaceLogRemainDays();
		SimpleDateFormat sFormat = new SimpleDateFormat("yyyyMMdd");

		Date today = new Date();
		String dn = sFormat.format(new Date(today.getTime() - days * 24 * 60 * 60 * 1000));

		File imgDir = new File(Config.getInstance().getImagesLogDir());

		if (imgDir.exists()) {
			File dirs[] = imgDir.listFiles();
			for (File d : dirs) {
				if (d.isDirectory()) {
					if (d.getName().compareTo(dn) < 0) {
						if (CommUtil.deleteDir(d)) {
							System.out.println("Remove face log dir " + d.getAbsolutePath());
						}
					}
				}
			}
		}

	}

	public static void saveFaceDataToDsk(PITVerifyData fd) {
		if ((Config.getInstance().getIsSaveFaceImageToLocaldisk() == 1)) {
			String dirName = Config.getInstance().getImagesLogDir();
			SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
			dirName += formatter.format(new Date());
			String trackedDir = dirName + "/Tracked";
			String passedDir = dirName + "/Passed";
			String failedDir = dirName + "/Failed";

			float result = fd.getVerifyResult();
			if (result >= Config.getInstance().getFaceCheckThreshold()) {
				saveFrameImage(passedDir, fd);
				saveFaceImage(passedDir, fd);
				saveIDCardImage(passedDir, fd);
			} else if (result < Config.getInstance().getFaceCheckThreshold() && result > 0) {
				saveFrameImage(failedDir, fd);
				saveFaceImage(failedDir, fd);
				saveIDCardImage(failedDir, fd);
			} 
		}

	}

	private static void saveFrameImage(String dirName, PITVerifyData fd) {
		if (fd == null || fd.getFrameImg() == null)
			return;
		int ret = CommUtil.createDir(dirName);
		if (ret == 0 || ret == 1) {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss-SSS");

			StringBuffer sb = new StringBuffer();
			sb.append(dirName);
			sb.append("/VF");
			if (fd.getIdNo() != null)
				sb.append(fd.getIdNo().hashCode());
			else
				sb.append("--");
			sb.append("@");
			sb.append(formatter.format(new Date()));
			sb.append(".jpg");
			String fn = sb.toString();
			DataOutputStream out;
			if (fd.getFaceImg() != null) {
				try {
					out = new DataOutputStream(new FileOutputStream(fn));
					out.write(fd.getFrameImg());
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private static void saveFaceImage(String dirName, PITVerifyData fd) {
		if (fd == null || fd.getFaceImg() == null)
			return;
		int ret = CommUtil.createDir(dirName);
		if (ret == 0 || ret == 1) {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss-SSS");
			DecimalFormat df = (DecimalFormat) NumberFormat.getInstance();
			df.setMaximumFractionDigits(2);
			StringBuffer sb = new StringBuffer();
			sb.append(dirName);
			sb.append("/FI");
			if (fd.getIdNo() != null)
				sb.append(fd.getIdNo().hashCode());
			else
				sb.append("--");
			sb.append("@");
			sb.append(formatter.format(new Date()));
			sb.append("$");
			sb.append(df.format(fd.getVerifyResult()));
			sb.append(".jpg");
			String fn = sb.toString();

			DataOutputStream out;
			if (fd.getFaceImg() != null) {
				try {
					out = new DataOutputStream(new FileOutputStream(fn));
					out.write(fd.getFaceImg());
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private static void saveIDCardImage(String dirName, PITVerifyData fd) {
		if (fd == null || fd.getIdCardImg() == null)
			return;

		int ret = CommUtil.createDir(dirName);
		if (ret == 0 || ret == 1) {
			StringBuffer sb = new StringBuffer();
			sb.append(dirName);
			sb.append("/ID");
			sb.append(fd.getIdNo().hashCode());
			sb.append(".jpg");
			String fn = sb.toString();
			DataOutputStream out;
			if (fd.getFaceImg() != null) {
				try {
					out = new DataOutputStream(new FileOutputStream(fn));
					out.write(fd.getIdCardImg());
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		FaceImageLog.clearFaceLogs();
	}
}
