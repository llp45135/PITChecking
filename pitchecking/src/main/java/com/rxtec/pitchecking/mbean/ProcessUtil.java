package com.rxtec.pitchecking.mbean;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Date;

import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.utils.DateUtils;

public class ProcessUtil {
	private static Logger log = LoggerFactory.getLogger("RSFaceTrackTask");

	public static void main(String[] args) {
		String pid = getCurrentProcessID();
		System.out.println(pid + " write ret = " + writeHeartbeat(pid));
		System.out.println("last heartbeat time = " + getLastHeartBeat());

	}

	public static String getCurrentProcessID() {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		String pid = name.split("@")[0];
		return pid;

	}

	public static boolean writeHeartbeat(String pid) {
		Date now = new Date();
		String nowTime = DateUtils.getStringDateHaomiao();// Long.toString(now.getTime());
		try {
			File logFile = new File(Config.getInstance().getHeartBeatLogFile());
			if (!logFile.exists()) {
				logFile.createNewFile();
			}
			FileWriter fw = new FileWriter(logFile);
			String pidStr = pid + "@" + nowTime;
//			log.debug("pidStr==" + pidStr);
			fw.write(pidStr);
			fw.flush();
			fw.close();
			return true;

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return false;
		}
	}

	public static String getLastHeartBeat() {
		String s = "";
		try {
			File f = new File(Config.getInstance().getHeartBeatLogFile());
			if (!f.exists())
				s = "";
			else {
				FileInputStream fis = new FileInputStream(f);
				InputStreamReader reader = new InputStreamReader(fis);
				BufferedReader bufferedReader = new BufferedReader(reader);
				s = bufferedReader.readLine();
				fis.close();
				reader.close();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			s = "";
		}
		return s;
	}

}
