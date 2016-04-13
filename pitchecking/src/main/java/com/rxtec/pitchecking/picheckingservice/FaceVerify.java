package com.rxtec.pitchecking.picheckingservice;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Calendar;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xvolks.jnative.JNative;
import org.xvolks.jnative.Type;
import org.xvolks.jnative.exceptions.NativeException;
import org.xvolks.jnative.pointers.Pointer;
import org.xvolks.jnative.pointers.memory.MemoryBlockFactory;

public class FaceVerify {
	
	public FaceVerify(){
		initJNIContext();
	}
	JNative jnative1 = null;
	JNative jnative2 = null;
	JNative jnative3 = null;
	private Logger log = LoggerFactory.getLogger("FaceAuthentication");
	
	
	private void initJNIContext(){
		try {
			jnative1 = new JNative("FaceVRISDK.dll","PS_InitFaceIDSDK");
			jnative1.setRetVal(Type.INT); 
			jnative1.invoke(); 
			log.debug("PS_InitFaceIDSDK ret: " + jnative1.getRetVal());//获取返回值
			jnative2 = new JNative("FaceVRISDK.dll","PS_VerifyImage");
		}catch (NativeException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}  finally {   
		}


	}
	
	public void clearJNIContext(){
		try{
			jnative3 = new JNative("FaceVRISDK.dll","PS_ExitFaceIDSDK");
			jnative3.setRetVal(Type.INT); 
			jnative3.invoke(); 
			log.debug("PS_ExitFaceIDSDK ret: " + jnative3.getRetVal());//获取返回值
			jnative3.dispose(); //注意：JNative1.3.2任意调用一个一次就可以了，不允许多次
		}catch (NativeException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}  finally {   
		}
	}
	
	public float verify(byte[] frameBytes, byte[] idCardBytes) {
		float result = 0;
		
		long nowMils = Calendar.getInstance().getTimeInMillis();
		
		try {

			Pointer aArrIntInputf = new Pointer(MemoryBlockFactory.createMemoryBlock(8));
			aArrIntInputf.setFloatAt(0, -1);
			int i = 0; 
			log.debug("aArrIntInputf: " + aArrIntInputf.getAsFloat(0));//获取返回值

			jnative2.setRetVal(Type.INT); 
			jnative2.setParameter(i++, Type.STRING,frameBytes); 
			jnative2.setParameter(i++, Type.INT,""+frameBytes.length); 
			jnative2.setParameter(i++, Type.STRING,idCardBytes); 
			jnative2.setParameter(i++, Type.INT,""+idCardBytes.length); 
			jnative2.setParameter(i++, aArrIntInputf);
			jnative2.invoke();
			//打印函数返回值 
			//log.debug("PS_VerifyImage retCode=" + jnative3.getRetValAsInt());//获取返回值

			//获取输出参数值（验证分数值）
			result = aArrIntInputf.getAsFloat(0);
			aArrIntInputf.dispose();
			long usingTime = Calendar.getInstance().getTimeInMillis() - nowMils;
			log.debug("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!FaceChecking succ, using " + usingTime + " ms, value=" + result);
			
			
		} catch (NativeException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}  finally {   
		}
		return result;
	}
	
	
	public static void main(String[] args) {

		FaceVerify v = new FaceVerify();
		IDCard c1 = createIDCard("C:/DCZ/20160412/43040619900308255x.jpg");
		IDCard c2 = createIDCard("C:/DCZ/20160412/out.jpg");
				
		v.verify(c1.getImageBytes(), c2.getImageBytes());
		v.verify(c1.getImageBytes(), c2.getImageBytes());
		v.verify(c1.getImageBytes(), c2.getImageBytes());
		v.verify(c1.getImageBytes(), c2.getImageBytes());
		v.verify(c1.getImageBytes(), c2.getImageBytes());
		v.verify(c1.getImageBytes(), c2.getImageBytes());

	}

	
	private static IDCard createIDCard(String fn){
		IDCard card = new IDCard();
		BufferedImage bi = null;
		try {
			bi = ImageIO.read(new File(fn));
		} catch (Exception e) {
			e.printStackTrace();
		}

		card.setCardImage(bi);
		return card;
	}
}
