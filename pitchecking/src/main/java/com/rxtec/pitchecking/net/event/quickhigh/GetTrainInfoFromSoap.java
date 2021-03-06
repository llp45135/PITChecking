package com.rxtec.pitchecking.net.event.quickhigh;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.DeviceEventListener;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.utils.CommUtil;

public class GetTrainInfoFromSoap {
	private Logger log = LoggerFactory.getLogger("DeviceEventListener");

	public GetTrainInfoFromSoap() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			(new GetTrainInfoFromSoap()).sendSms();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
	public void sendSms() throws Exception {
		String sequenceNo = DeviceConfig.getInstance().getBelongStationCode();
		String urlString = "http://192.168.1.107:9100/ServiceTrainInfo.asmx";

		String filePath = Thread.currentThread().getContextClassLoader().getResource("").toString();
		String fn = filePath + "conf/SendInstantSms.xml";
		URL fileurl = null;
		boolean flag = false;
		try {
			fileurl = new URL(fn);
			flag = true;
		} catch (Exception ex) {
			// log.error("Config build:", ex);
			flag = false;
		}

		if (!flag) {
			try {
				fn = filePath + "SendInstantSms.xml";
				fileurl = new URL(fn);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		log.info("fileurl==" + fileurl.getFile());
		String xmlFile = replace(fileurl.getFile(), "string", sequenceNo).getPath();
		String soapActionString = "http://Easyway.net.cn/GetRailwayDynamicDatas";
		URL url = new URL(urlString);
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		File fileToSend = new File(xmlFile);
		byte[] buf = new byte[(int) fileToSend.length()];
		new FileInputStream(xmlFile).read(buf);
		httpConn.setRequestProperty("Content-Length", String.valueOf(buf.length));
		httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
		httpConn.setRequestProperty("soapActionString", soapActionString);
		httpConn.setRequestMethod("POST");
		httpConn.setDoOutput(true);
		httpConn.setDoInput(true);
		OutputStream out = httpConn.getOutputStream();
		out.write(buf);
		out.close();

		byte[] datas = readInputStream(httpConn.getInputStream());
		String result = new String(datas, "utf-8");
		// 打印返回结果
		// System.out.println(result);

		MessageFactory msgFactory;
		try {
			ArrayList<TrainInfomation> trainList = new ArrayList<TrainInfomation>();

			msgFactory = MessageFactory.newInstance();
			SOAPMessage reqMsg = msgFactory.createMessage(new MimeHeaders(), new ByteArrayInputStream(result.getBytes("UTF-8")));
			reqMsg.saveChanges();
			SOAPBody body = reqMsg.getSOAPBody();
			Iterator<SOAPElement> iterator = body.getChildElements();
			while (iterator.hasNext()) {
				SOAPElement element = iterator.next();
				if (element.getNodeName().equals("GetRailwayDynamicDatasResponse")) {
					Iterator<SOAPElement> it = element.getChildElements();
					while (it.hasNext()) {
						SOAPElement ele1 = it.next();
						if (ele1.getNodeName().equals("GetRailwayDynamicDatasResult")) {
							Iterator<SOAPElement> ite2 = ele1.getChildElements();
							while (ite2.hasNext()) {
								SOAPElement ele2 = ite2.next();
								// System.out.println(ele2.getNodeName());
								if (ele2.getNodeName().equals("diffgr:diffgram")) {
									Iterator<SOAPElement> ite3 = ele2.getChildElements();
									while (ite3.hasNext()) {
										SOAPElement ele3 = ite3.next();
										// System.out.println(""+ele3.getNodeName());
										if (ele3.getNodeName().equals("VI_MPS_VIEW")) {
											Iterator<SOAPElement> ite4 = ele3.getChildElements();
											int i = 0;
											while (ite4.hasNext()) {
												SOAPElement ele4 = ite4.next();
												i++;
												TrainInfomation train = new TrainInfomation();
												// System.out.println(""+ele4.getNodeName());
												// System.out.println("/********************"
												// + i +
												// "**************************/");
												if (ele4.getNodeName().equals("Table")) {
													Iterator<SOAPElement> ite5 = ele4.getChildElements();
													while (ite5.hasNext()) {
														SOAPElement ele5 = ite5.next();

														if (ele5.getNodeName().equals("ArriveTrainNo")) {
															train.setArriveTrainNo(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("DepartTrainNo")) {
															train.setDepartTrainNo(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("StartStation")) {
															train.setStartStation(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("TerminalStation")) {
															train.setTerminalStation(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("TrainStatus")) {
															train.setTrainStatus(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("TrainDate")) {
															train.setTrainDate(ele5.getTextContent().substring(0, 10));
														}
														if (ele5.getNodeName().equals("PlanArriveTime")) {
															train.setPlanArriveTime(
																	ele5.getTextContent().substring(0, 10) + " " + ele5.getTextContent().substring(11, 19));
														}
														if (ele5.getNodeName().equals("PlanDepartTime")) {
															train.setPlanDepartTime(
																	ele5.getTextContent().substring(0, 10) + " " + ele5.getTextContent().substring(11, 19));
														}
														if (ele5.getNodeName().equals("ArriveTime")) {
															train.setArriveTime(ele5.getTextContent().substring(0, 10) + " " + ele5.getTextContent().substring(11, 19));
														}
														if (ele5.getNodeName().equals("DepartTime")) {
															train.setDepartTime(ele5.getTextContent().substring(0, 10) + " " + ele5.getTextContent().substring(11, 19));
														}
														if (ele5.getNodeName().equals("track")) {
															train.setTrack(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("WaitRoom")) {
															train.setWaitRoom(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("TicketGate")) {
															train.setTicketGate(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("TrainType")) {
															train.setTrainType(ele5.getTextContent());
														}
														if (ele5.getNodeName().equals("WorkStatus")) {
															train.setWorkStatus(ele5.getTextContent());
														}
													}
												}
												trainList.add(train);
											}
										}
									}
								}
							}
						}
					}
				}
			}

			log.info("trainList.size = " + trainList.size());
			if (trainList.size() > 0) {
				Iterator ite = trainList.iterator();
				StringBuffer fileBuffer = new StringBuffer();
				int i = 0;
				int total = trainList.size();
				while (ite.hasNext()) {
					TrainInfomation tis = (TrainInfomation) ite.next();
					StringBuffer sb = new StringBuffer();
					sb.append(tis.getDepartTrainNo()).append(",");
					// sb.append(tis.getDepartTrainNo()).append(",");
					sb.append(tis.getStartStation()).append(",");
					sb.append(tis.getTerminalStation()).append(",");
					// sb.append(tis.getTrainDate()).append(",");
					sb.append(tis.getTrainStatus()).append(",");
					sb.append(tis.getPlanDepartTime()).append(",");
					sb.append(tis.getDepartTime()).append(",");
					// sb.append(tis.getWaitRoom()).append(",");
					// sb.append(tis.getTrainType()).append(",");
					// sb.append(tis.getWorkStatus()).append(",");
					log.info("" + sb.toString());

					String trainKey = tis.getDepartTrainNo() + tis.getPlanDepartTime().substring(0, 10);
					DeviceEventListener.getInstance().getTrainInfoMap().put(trainKey, tis);

					fileBuffer.append(tis.getDepartTrainNo() + "@" + tis.getPlanDepartTime() + "@" + tis.getDepartTime() + "@" + tis.getTrainStatus() + "@"
							+ DeviceConfig.getInstance().getBelongStationCode() + "$" + tis.getWaitRoom());
					i++;
					if (i < total)
						fileBuffer.append("\r\n");
				}
				CommUtil.writeFileContent("d:/ftphome/train_list.txt", fileBuffer.toString());
			}
		} catch (Exception e) {
			log.error("", e);
		}
	}

	/**
	 * 文件内容替换
	 * 
	 * @param inFileName
	 *            源文件
	 * @param from
	 * @param to
	 * @return 返回替换后文件
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 */
	public static File replace(String inFileName, String from, String to) throws IOException, UnsupportedEncodingException {
		File inFile = new File(inFileName);
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "utf-8"));
		File outFile = new File(inFile + ".tmp");
		PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "utf-8")));
		String reading;
		while ((reading = in.readLine()) != null) {
			out.println(reading.replaceAll(from, to));
		}
		out.close();
		in.close();
		// infile.delete(); //删除源文件
		// outfile.renameTo(infile); //对临时文件重命名
		return outFile;
	}

	/**
	 * 从输入流中读取数据
	 * 
	 * @param inStream
	 * @return
	 * @throws Exception
	 */
	public static byte[] readInputStream(InputStream inStream) throws Exception {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while ((len = inStream.read(buffer)) != -1) {
			outStream.write(buffer, 0, len);
		}
		byte[] data = outStream.toByteArray();// 网页的二进制数据
		outStream.close();
		inStream.close();
		return data;
	}
}
