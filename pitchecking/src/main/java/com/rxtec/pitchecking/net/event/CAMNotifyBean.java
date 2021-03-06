package com.rxtec.pitchecking.net.event;

import com.rxtec.pitchecking.Ticket;
import com.rxtec.pitchecking.utils.CalUtils;

public class CAMNotifyBean {
	private String eventName = "CAM_Notify";
	private int eventDirection = 1;
	private String uuid; // 身份证号码
	private byte[] idPhoto;
	private String personName;
	private int age;
	private int gender;
	private String idBirth = ""; // 出生日期
	private String idNation = ""; // 民族
	private String idDwelling = "";
	private String idIssue = "";
	private String idEfficb = "";
	private String idEffice = "";

	private Ticket ticket;
	private int delaySeconds;

	public String getIdBirth() {
		return idBirth;
	}

	public void setIdBirth(String idBirth) {
		this.idBirth = idBirth;
	}

	public String getIdNation() {
		return idNation;
	}

	public void setIdNation(String idNation) {
		this.idNation = idNation;
	}

	public String getIdDwelling() {
		return idDwelling;
	}

	public void setIdDwelling(String idDwelling) {
		this.idDwelling = idDwelling;
	}

	public String getIdIssue() {
		return idIssue;
	}

	public void setIdIssue(String idIssue) {
		this.idIssue = idIssue;
	}

	public String getIdEfficb() {
		return idEfficb;
	}

	public void setIdEfficb(String idEfficb) {
		this.idEfficb = idEfficb;
	}

	public String getIdEffice() {
		return idEffice;
	}

	public void setIdEffice(String idEffice) {
		this.idEffice = idEffice;
	}

	public int getDelaySeconds() {
		return delaySeconds;
	}

	public void setDelaySeconds(int delaySeconds) {
		this.delaySeconds = delaySeconds;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public int getEventDirection() {
		return eventDirection;
	}

	public void setEventDirection(int eventDirection) {
		this.eventDirection = eventDirection;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
		this.convertIDNo(uuid);
	}

	// 根据身份证号码转换年龄和性别
	private void convertIDNo(String uuid) {
		if (uuid == null || uuid.length() != 18)
			return;
		// String gs = uuid.substring(14, 16);
		// int i = Integer.parseInt(gs);
		// int g = i & 1;
		// this.setGender(g);
		// String as = uuid.substring(6,9);
		// int by = Integer.parseInt(as);
		// Calendar c = Calendar.getInstance();
		// c.setTime(new Date());
		// int year = c.get(Calendar.YEAR);

		String birthstr = uuid.substring(6, 14);
		String birthday = birthstr.substring(0, 4) + "-" + birthstr.substring(4, 6) + "-" + birthstr.substring(6, 8);
		String today = CalUtils.getStringDateShort();
		int personAge = CalUtils.getAge(birthday, today);
		this.setAge(personAge);
	}

	public byte[] getIdPhoto() {
		return idPhoto;
	}

	public void setIdPhoto(byte[] idPhoto) {
		this.idPhoto = idPhoto;
	}

	public String getPersonName() {
		return personName;
	}

	public void setPersonName(String personName) {
		this.personName = personName;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public int getGender() {
		return gender;
	}

	public void setGender(int gender) {
		this.gender = gender;
	}

	public Ticket getTicket() {
		return ticket;
	}

	public void setTicket(Ticket ticket) {
		this.ticket = ticket;
	}

}
