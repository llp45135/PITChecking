package com.rxtec.pitchecking.device;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xvolks.jnative.JNative;
import org.xvolks.jnative.Type;
import org.xvolks.jnative.exceptions.NativeException;
import org.xvolks.jnative.pointers.Pointer;
import org.xvolks.jnative.pointers.memory.MemoryBlockFactory;

import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.IDCard;

import com.rxtec.pitchecking.utils.CommUtil;
import com.rxtec.pitchecking.utils.IDCardUtil;
import com.rxtec.pitchecking.utils.CalUtils;

public class RSIVIDcardDevice {
	private Logger log = LoggerFactory.getLogger("RSIVIDcardDevice");
	// private String dllName = "IDC_EWTa.dll";
//	private String dllName = "C:\\RSIV\\DRIVER\\IDC\\RSIV_IDCARD.dll";
	private String dllName = "RSIV_IDCARD.dll";
	private String bmpDllName = "WltRS.dll";

	JNative jnativeIDC_Init = null;
	JNative jnativeIDC_FetchCard = null;
	JNative jnativeIDC_StopFetchCard = null;
	JNative jnativeIDC_GetSerial = null;
	JNative jnativeIDC_ReadIDCardInfo = null;
	JNative jnativeIDC_Check = null;
	JNative jnativeIDC_GetVersion = null;
	JNative jnativeBmp = null;
	static RSIVIDcardDevice _instance = new RSIVIDcardDevice();
	private int idcInitRet = -1;

	public static RSIVIDcardDevice getInstance() {
		return _instance;
	}

	private RSIVIDcardDevice() {
		JNative.setLoggingEnabled(false);
		this.initJnative();
		this.IDC_Init();
		// DeviceConfig.getInstance().setIDCardReaderID(this.IDC_GetSerial());
	}

	public int getIdcInitRet() {
		return idcInitRet;
	}

	public void setIdcInitRet(int idcInitRet) {
		this.idcInitRet = idcInitRet;
	}

	/**
	 * 初始化jnative
	 */
	private void initJnative() {

		try {
			jnativeIDC_Init = new JNative(dllName, "IDC_Init");
			jnativeIDC_FetchCard = new JNative(dllName, "IDC_FetchCard");
			jnativeIDC_StopFetchCard = new JNative(dllName, "IDC_StopFetchCard");
			jnativeIDC_GetSerial = new JNative(dllName, "IDC_GetSerial");
			jnativeIDC_ReadIDCardInfo = new JNative(dllName, "IDC_ReadIDCardInfo");
			jnativeIDC_Check = new JNative(dllName, "IDC_Check");
			jnativeIDC_GetVersion = new JNative(dllName, "IDC_GetVersion");
			jnativeBmp = new JNative(bmpDllName, "GetBmp");

		} catch (NativeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 对二代居民身份证识读单元进行初始化设置
	 */
	public void IDC_Init() {
		String retval = "";
		Pointer pointerOut = null;

		try {
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4 * 4 + 128 + 128));
			int i = 0;

			jnativeIDC_Init.setRetVal(Type.INT);
			jnativeIDC_Init.setParameter(i++, pointerOut);
			jnativeIDC_Init.invoke();

			retval = jnativeIDC_Init.getRetVal();
			log.debug("IDC_Init retval==" + retval);
			if (retval.equals("0")) {
				this.setIdcInitRet(0);
				byte[] iLogicCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iLogicCode[k] = pointerOut.getAsByte(k);
				}
				log.debug("iLogicCode==" + CommUtil.bytesToInt(iLogicCode, 0));

				byte[] iPhyCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iPhyCode[k] = pointerOut.getAsByte(k + 4);
				}
				log.debug("iPhyCode==" + CommUtil.bytesToInt(iPhyCode, 0));

				byte[] iHandle = new byte[4];
				for (int k = 0; k < 4; k++) {
					iHandle[k] = pointerOut.getAsByte(k + 4 + 4);
				}
				log.debug("iHandle==" + CommUtil.bytesToInt(iHandle, 0));

				byte[] iType = new byte[4];
				for (int k = 0; k < 4; k++) {
					iType[k] = pointerOut.getAsByte(k + 4 + 4 + 4);
				}
				log.debug("iType==" + CommUtil.bytesToInt(iType, 0));

				byte[] acDevReturn = new byte[128];
				for (int k = 0; k < 128; k++) {
					acDevReturn[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4);
				}
				log.debug("acDevReturn==" + new String(acDevReturn));

				byte[] acReserve = new byte[128];
				for (int k = 0; k < 128; k++) {
					acReserve[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4 + 128);
				}
				log.debug("acReserve==" + new String(acReserve));
			} else {
				this.setIdcInitRet(Integer.parseInt(retval));
			}
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_Init:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_Init:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_Init:", e);
		}
	}

	/**
	 * 读取二代居民身份证识读单元序列号
	 */
	public String IDC_GetSerial() {
		String retval = "";
		String cSerialNoStr = "";
		Pointer pointerOut = null;

		try {
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(32));
			int i = 0;

			jnativeIDC_GetSerial.setRetVal(Type.INT);
			jnativeIDC_GetSerial.setParameter(i++, pointerOut);
			jnativeIDC_GetSerial.invoke();

			retval = jnativeIDC_GetSerial.getRetVal();
			log.info("IDC_GetSerial retval==" + retval);
			if (retval.equals("0")) {
				byte[] cSerialNo = new byte[32];
				for (int k = 0; k < 32; k++) {
					cSerialNo[k] = pointerOut.getAsByte(k);
				}
				cSerialNoStr = new String(cSerialNo, "GBK");
				log.info("cSerialNo==" + cSerialNoStr);
			}
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_GetSerial:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_GetSerial:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_GetSerial:", e);
		}
		return cSerialNoStr;
	}

	/**
	 * 
	 * @return
	 */
	public int IDC_Check() {
		int isHasCard = 0;
		String retval = "";
		Pointer pointerOut = null;
		try {
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4));
			int i = 0;

			jnativeIDC_Check.setRetVal(Type.INT);
			jnativeIDC_Check.setParameter(i++, pointerOut);
			jnativeIDC_Check.invoke();

			retval = jnativeIDC_Check.getRetVal();
			log.debug("IDC_Check retval==" + retval);
			if (retval.equals("0")) {
				byte[] iStatus = new byte[4];
				for (int k = 0; k < 4; k++) {
					iStatus[k] = pointerOut.getAsByte(k);
				}
				log.debug("iStatus==" + CommUtil.bytesToInt(iStatus, 0));
				isHasCard = CommUtil.bytesToInt(iStatus, 0);
			}
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_Check:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_Check:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_Check:", e);
		}
		return isHasCard;
	}

	/**
	 * 
	 * @return
	 */
	public String IDC_GetVersion() {
		String retval = "-1";
		Pointer pointercVersion = null;
		Pointer pointerDevOut = null;

		try {
			pointercVersion = new Pointer(MemoryBlockFactory.createMemoryBlock(32));
			pointerDevOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4 * 4 + 128 + 128));
			int i = 0;

			jnativeIDC_GetVersion.setRetVal(Type.INT);
			jnativeIDC_GetVersion.setParameter(i++, pointercVersion);
			jnativeIDC_GetVersion.setParameter(i++, pointerDevOut);
			jnativeIDC_GetVersion.invoke();

			retval = jnativeIDC_GetVersion.getRetVal();
			log.debug("jnativeIDC_GetVersion retval==" + retval);
			if (retval.equals("0")) {
				log.debug("读取二代居民身份证识读单元驱动版本详细信息");
				byte[] cVersion = new byte[32];
				for (int k = 0; k < 32; k++) {
					cVersion[k] = pointercVersion.getAsByte(k);
				}
				log.info("cVersion==" + new String(cVersion));

				byte[] iLogicCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iLogicCode[k] = pointerDevOut.getAsByte(k);
				}
				log.info("iLogicCode==" + CommUtil.bytesToInt(iLogicCode, 0));

				byte[] iPhyCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iPhyCode[k] = pointerDevOut.getAsByte(k + 4);
				}
				log.info("iPhyCode==" + CommUtil.bytesToInt(iPhyCode, 0));

				byte[] iHandle = new byte[4];
				for (int k = 0; k < 4; k++) {
					iHandle[k] = pointerDevOut.getAsByte(k + 4 + 4);
				}
				log.info("iHandle==" + CommUtil.bytesToInt(iHandle, 0));

				byte[] iType = new byte[4];
				for (int k = 0; k < 4; k++) {
					iType[k] = pointerDevOut.getAsByte(k + 4 + 4 + 4);
				}
				log.info("iType==" + CommUtil.bytesToInt(iType, 0));

				byte[] acDevReturn = new byte[128];
				for (int k = 0; k < 128; k++) {
					acDevReturn[k] = pointerDevOut.getAsByte(k + 4 + 4 + 4 + 4);
				}
				log.info("acDevReturn==" + new String(acDevReturn, "GBK"));

				byte[] acReserve = new byte[128];
				for (int k = 0; k < 128; k++) {
					acReserve[k] = pointerDevOut.getAsByte(k + 4 + 4 + 4 + 4 + 128);
				}
				log.info("acReserve==" + new String(acReserve, "GBK"));
			}
			pointercVersion.dispose();
			pointerDevOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice jnativeIDC_GetVersion:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice jnativeIDC_GetVersion:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice jnativeIDC_GetVersion:", e);
		}
		return retval;
	}

	/**
	 * 在设定时间内寻找是否有二代居民身份证进入部件读取有效范围
	 * 
	 * @param iTimeOut
	 * @return
	 */
	public int IDC_FetchCard(int iTimeOut) {
		String retval = "-1";
		Pointer pointerCardType = null;
		Pointer pointerOut = null;
		int iLogicCodeNum = -1;
		try {
			pointerCardType = new Pointer(MemoryBlockFactory.createMemoryBlock(4));
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4 * 4 + 128 + 128));
			int i = 0;

			jnativeIDC_FetchCard.setRetVal(Type.INT);
			jnativeIDC_FetchCard.setParameter(i++, iTimeOut);
			jnativeIDC_FetchCard.setParameter(i++, pointerCardType);
			jnativeIDC_FetchCard.setParameter(i++, pointerOut);
			jnativeIDC_FetchCard.invoke();

			retval = jnativeIDC_FetchCard.getRetVal();
			log.debug("IDC_FetchCard retval==" + retval);

			byte[] iCardType = new byte[4];
			for (int k = 0; k < 4; k++) {
				iCardType[k] = pointerCardType.getAsByte(k);
			}
			int iCardTypeNum = CommUtil.bytesToInt(iCardType, 0);
			log.debug("iCardType==" + iCardTypeNum);

			byte[] iLogicCode = new byte[4];
			for (int k = 0; k < 4; k++) {
				iLogicCode[k] = pointerOut.getAsByte(k);
			}
			iLogicCodeNum = CommUtil.bytesToInt(iLogicCode, 0);
			log.debug("iLogicCode==" + iLogicCodeNum);

			byte[] iPhyCode = new byte[4];
			for (int k = 0; k < 4; k++) {
				iPhyCode[k] = pointerOut.getAsByte(k + 4);
			}
			log.debug("iPhyCode==" + CommUtil.bytesToInt(iPhyCode, 0));

			byte[] iHandle = new byte[4];
			for (int k = 0; k < 4; k++) {
				iHandle[k] = pointerOut.getAsByte(k + 4 + 4);
			}
			log.debug("iHandle==" + CommUtil.bytesToInt(iHandle, 0));

			byte[] iType = new byte[4];
			for (int k = 0; k < 4; k++) {
				iType[k] = pointerOut.getAsByte(k + 4 + 4 + 4);
			}
			log.debug("iType==" + CommUtil.bytesToInt(iType, 0));

			byte[] acDevReturn = new byte[128];
			for (int k = 0; k < 128; k++) {
				acDevReturn[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4);
			}
			log.debug("acDevReturn==" + new String(acDevReturn, "GBK"));

			byte[] acReserve = new byte[128];
			for (int k = 0; k < 128; k++) {
				acReserve[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4 + 128);
			}
			log.debug("acReserve==" + new String(acReserve, "GBK"));
			if (iCardTypeNum == 1) {
				log.debug("已经寻到二代证");
			} else {
				log.debug("未寻到二代证");
			}
			pointerCardType.dispose();
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_FetchCard:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_FetchCard:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_FetchCard:", e);
		}
		return iLogicCodeNum;
	}

	/**
	 * 停止二代居民身份证识读单元寻找是否有二代居民身份证进入部件读取有效范围
	 */
	public void IDC_StopFetchCard() {
		String retval = "";
		Pointer pointerOut = null;

		try {
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4 * 4 + 128 + 128));
			int i = 0;

			jnativeIDC_StopFetchCard.setRetVal(Type.INT);
			jnativeIDC_StopFetchCard.setParameter(i++, pointerOut);
			jnativeIDC_StopFetchCard.invoke();

			retval = jnativeIDC_StopFetchCard.getRetVal();
			log.debug("jnativeIDC_StopFetchCard retval==" + retval);
			if (retval.equals("0")) {
				byte[] iLogicCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iLogicCode[k] = pointerOut.getAsByte(k);
				}
				// log.debug("iLogicCode==" + CommUtil.bytesToInt(iLogicCode,
				// 0));

				byte[] iPhyCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iPhyCode[k] = pointerOut.getAsByte(k + 4);
				}
				// log.debug("iPhyCode==" + CommUtil.bytesToInt(iPhyCode, 0));

				byte[] iHandle = new byte[4];
				for (int k = 0; k < 4; k++) {
					iHandle[k] = pointerOut.getAsByte(k + 4 + 4);
				}
				// log.debug("iHandle==" + CommUtil.bytesToInt(iHandle, 0));

				byte[] iType = new byte[4];
				for (int k = 0; k < 4; k++) {
					iType[k] = pointerOut.getAsByte(k + 4 + 4 + 4);
				}
				// log.debug("iType==" + CommUtil.bytesToInt(iType, 0));

				byte[] acDevReturn = new byte[128];
				for (int k = 0; k < 128; k++) {
					acDevReturn[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4);
				}
				// log.debug("acDevReturn==" + new String(acDevReturn));

				byte[] acReserve = new byte[128];
				for (int k = 0; k < 128; k++) {
					acReserve[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4 + 128);
				}
				// log.debug("acReserve==" + new String(acReserve));
			}
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_StopFetchCard:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_StopFetchCard:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_StopFetchCard:", e);
		}
	}

	/**
	 * 根据指定的类型读取二代居民身份证信息。 输入参数： iType 读取二代居民身份证信息类型。
	 * 1：读取除照片外的文本信息，包括二代证卡号、姓名、性别、名族、出生日期、有效期限、住址 2: 读取二代居民身份证所有信息 输出参数：
	 * p_psCardInfo 保存二代居民身份证信息。 p_psStatus 保存状态信息。 返回值： int 0：成功 1：失败
	 * 
	 * @param iType
	 * @return
	 */
	public IDCard IDC_ReadIDCardInfo(int iType) {
		String retval = "-1";
		Pointer pointerCardInfo = null;
		Pointer pointerOut = null;
		IDCard synIDCard = null;
		try {
			pointerCardInfo = new Pointer(MemoryBlockFactory.createMemoryBlock(64 + 2 + 4 + 16 + 128 + 36 + 36 + 16 + 16 + 70 + 1024));
			pointerOut = new Pointer(MemoryBlockFactory.createMemoryBlock(4 * 4 + 128 + 128));
			int i = 0;

			jnativeIDC_ReadIDCardInfo.setRetVal(Type.INT);
			jnativeIDC_ReadIDCardInfo.setParameter(i++, iType);
			jnativeIDC_ReadIDCardInfo.setParameter(i++, pointerCardInfo);
			jnativeIDC_ReadIDCardInfo.setParameter(i++, pointerOut);
			jnativeIDC_ReadIDCardInfo.invoke();

			retval = jnativeIDC_ReadIDCardInfo.getRetVal();
			log.debug("IDC_ReadIDCardInfo retval==" + retval);
			if (retval.equals("0")) {
				synIDCard = new IDCard();

				byte[] IDName = new byte[64];
				for (int k = 0; k < IDName.length; k++) {
					IDName[k] = pointerCardInfo.getAsByte(k);
				}
				synIDCard.setIDNameArray(IDName);
				String personName = new String(IDName, "GBK").trim();
				log.debug("IDName==" + personName);
				synIDCard.setPersonName(personName); // set personName

				byte[] IDSex = new byte[2];
				for (int k = 0; k < IDSex.length; k++) {
					IDSex[k] = pointerCardInfo.getAsByte(k + 64);
				}
				synIDCard.setIDSexArray(IDSex);
				String gender = new String(IDSex, "GBK").trim();
				log.debug("IDSex==" + gender + "#");
				if (gender.equals("1")) {
					synIDCard.setGender(1);
					synIDCard.setGenderCH("男");// set gender
				} else if (gender.equals("2")) {
					synIDCard.setGender(2);
					synIDCard.setGenderCH("女");// set gender
				}

				byte[] IDNation = new byte[4];
				for (int k = 0; k < IDNation.length; k++) {
					IDNation[k] = pointerCardInfo.getAsByte(k + 64 + 2);
				}
				synIDCard.setIDNationArray(IDNation);
				String IDNationstr = new String(IDNation, "GBK").trim();
				log.debug("IDNation==" + IDNationstr + "#");
				synIDCard.setIDNation(IDNationstr);
				synIDCard.setIDNationCH(IDCardUtil.getIDNationCH(IDNationstr));

				byte[] IDBirth = new byte[16];
				for (int k = 0; k < IDBirth.length; k++) {
					IDBirth[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4);
				}
				synIDCard.setIDBirthArray(IDBirth);
				String birthstr = new String(IDBirth, "GBK").trim();
				log.debug("IDBirth==" + birthstr + "#");
				synIDCard.setIDBirth(birthstr);

				String birthday = birthstr.substring(0, 4) + "-" + birthstr.substring(4, 6) + "-" + birthstr.substring(6, 8);
				String today = CalUtils.getStringDateShort();
				// log.debug("birthday==" + birthday);
				int personAge = CalUtils.getAge(birthday, today);
				synIDCard.setAge(personAge); // set age
				// log.debug("出生年月：" + birthstr.substring(0, 4) + "年" +
				// birthstr.substring(4, 6) + "月"
				// + birthstr.substring(6, 8) + "日" + ",personAge==" +
				// personAge);

				byte[] IDDwelling = new byte[128];
				for (int k = 0; k < IDDwelling.length; k++) {
					IDDwelling[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16);
				}
				synIDCard.setIDDwellingArray(IDDwelling);
				String idDwelling = new String(IDDwelling, "GBK").trim();
				log.debug("IDDwelling==" + idDwelling);
				synIDCard.setIDDwelling(idDwelling);

				byte[] IDCode = new byte[36];
				for (int k = 0; k < IDCode.length; k++) {
					IDCode[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128);
				}
				synIDCard.setIDCodeArray(IDCode);
				String IDCardNoStr = new String(IDCode, "GBK").trim();
				log.debug("IDCode==" + IDCardNoStr);
				synIDCard.setIdNo(IDCardNoStr.trim());

				byte[] IDIssue = new byte[30];
				for (int k = 0; k < IDIssue.length; k++) {
					IDIssue[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128 + 36);
				}
				synIDCard.setIDIssueArray(IDIssue);
				String idIssue = new String(IDIssue, "GBK").trim();
				log.debug("IDIssue==" + idIssue);
				synIDCard.setIDIssue(idIssue);

				byte[] IDEfficb = new byte[16];
				for (int k = 0; k < IDEfficb.length; k++) {
					IDEfficb[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128 + 36 + 30);
				}
				synIDCard.setIDEfficbArray(IDEfficb);
				String idEfficb = new String(IDEfficb, "GBK").trim();
				log.debug("IDEfficb==" + idEfficb);
				synIDCard.setIDEfficb(idEfficb);

				byte[] IDEffice = new byte[16];
				for (int k = 0; k < IDEfficb.length; k++) {
					IDEffice[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128 + 36 + 30 + 16);
				}
				synIDCard.setIDEfficeArray(IDEffice);
				String idEffice = new String(IDEffice, "GBK").trim();
				log.debug("IDEffice==" + idEffice);
				synIDCard.setIDEffice(idEffice);

				byte[] IDNewAddr = new byte[70];
				for (int k = 0; k < IDNewAddr.length; k++) {
					IDNewAddr[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128 + 36 + 30 + 16 + 16);
				}
				synIDCard.setIDNewAddrArray(IDNewAddr);
				String idNewAddr = new String(IDNewAddr, "GBK").trim();
				log.debug("IDNewAddr==" + idNewAddr);
				synIDCard.setIDNewAddr(idNewAddr);

				byte[] IDPhoto = new byte[1024];
				for (int k = 0; k < IDPhoto.length; k++) {
					IDPhoto[k] = pointerCardInfo.getAsByte(k + 64 + 2 + 4 + 16 + 128 + 36 + 30 + 16 + 16 + 70);
				}
				synIDCard.setIDPhotoArray(IDPhoto);
				try {
					File myFile = new File("zp.wlt");
					FileOutputStream out = new FileOutputStream(myFile);
					out.write(IDPhoto, 0, 1024 - 1);
				} catch (IOException t) {
					t.printStackTrace();
				}

				/**
				 * Syn_GetBmp本函数用于将wlt文件解码成bmp文件 参数2：阅读设备通讯接口类型（1—RS-232C，2—USB）
				 */
				int j = 0;
				String bmpretval = "";

				jnativeBmp.setRetVal(org.xvolks.jnative.Type.INT);
				jnativeBmp.setParameter(j++, "zp.wlt");
				jnativeBmp.setParameter(j++, 1);
				jnativeBmp.invoke();
				bmpretval = jnativeBmp.getRetVal();
				log.debug("GetBmp: bmpretval==" + bmpretval);// 获取返回值

				CommUtil.bmpTojpg("zp.bmp", "zp.jpg");

				String photoFile = "zp.jpg";
				log.debug("photoFile==" + photoFile);
				File idcardFile = new File(photoFile);

				BufferedImage idCardImage = null;
				idCardImage = ImageIO.read(idcardFile);
				if (idCardImage != null) {
					log.debug("完整读取二代证照片成功！");
					synIDCard.setCardImage(idCardImage); // set cardImage
					byte[] idCardImageBytes = null;
					idCardImageBytes = CommUtil.getImageBytesFromImageBuffer(idCardImage);
					if (idCardImageBytes != null)
						synIDCard.setCardImageBytes(idCardImageBytes);
				}
				// boolean delFlag = idcardFile.delete();
				//

				byte[] iLogicCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iLogicCode[k] = pointerOut.getAsByte(k);
				}
				log.debug("iLogicCode==" + CommUtil.bytesToInt(iLogicCode, 0));

				byte[] iPhyCode = new byte[4];
				for (int k = 0; k < 4; k++) {
					iPhyCode[k] = pointerOut.getAsByte(k + 4);
				}
				log.debug("iPhyCode==" + CommUtil.bytesToInt(iPhyCode, 0));

				byte[] iHandle = new byte[4];
				for (int k = 0; k < 4; k++) {
					iHandle[k] = pointerOut.getAsByte(k + 4 + 4);
				}
				log.debug("iHandle==" + CommUtil.bytesToInt(iHandle, 0));

				byte[] errType = new byte[4];
				for (int k = 0; k < 4; k++) {
					errType[k] = pointerOut.getAsByte(k + 4 + 4 + 4);
				}
				log.debug("iType==" + CommUtil.bytesToInt(errType, 0));

				byte[] acDevReturn = new byte[128];
				for (int k = 0; k < 128; k++) {
					acDevReturn[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4);
				}
				log.debug("acDevReturn==" + new String(acDevReturn));

				byte[] acReserve = new byte[128];
				for (int k = 0; k < 128; k++) {
					acReserve[k] = pointerOut.getAsByte(k + 4 + 4 + 4 + 4 + 128);
				}
				log.debug("acReserve==" + new String(acReserve));
			}
			pointerCardInfo.dispose();
			pointerOut.dispose();
		} catch (NativeException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_ReadIDCardInfo:", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated IDC_ReadIDCardInfo block
			log.error("RSIVIDcardDevice IDC_ReadIDCardInfo:", e);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			log.error("RSIVIDcardDevice IDC_ReadIDCardInfo:", e);
		} catch (Exception e) {
			log.error("RSIVIDcardDevice IDC_ReadIDCardInfo:", e);
		}
		return synIDCard;
	}

	public static void main(String[] args) {

		RSIVIDcardDevice idc = RSIVIDcardDevice.getInstance();
		idc.IDC_Init();
		idc.IDC_GetSerial();
		idc.IDC_GetVersion();
		idc.IDC_Check();

		// while (true) {
		CommUtil.sleep(200);
		idc.IDC_FetchCard(3000);
		idc.IDC_ReadIDCardInfo(2);
		idc.IDC_Check();
		// }
	}
}
