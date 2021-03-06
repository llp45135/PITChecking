package com.rxtec.pitchecking.net;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.IDCard;
import com.rxtec.pitchecking.Ticket;
import com.rxtec.pitchecking.picheckingservice.FaceCheckingService;
import com.rxtec.pitchecking.picheckingservice.PITVerifyData;
import io.aeron.Aeron;
import io.aeron.Publication;

/**
 * 发布人脸比对结果（比对进程使用） 由人脸比对进程  -->  人脸录像检测进程
 * 此类用于睿新自有java主控闸机程序版本
 *
 */
public class PTVerifyResultPublisher {
	private Logger log = LoggerFactory.getLogger("PTVerifyResultPublisher");

	private static final int STREAM_ID = Config.PIVerify_Result_STREAM_ID;  // 11 独立人脸比对进程发布比对结果
	private static final String CHANNEL = Config.PIVerify_CHANNEL;
	private static final long LINGER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
	private static final UnsafeBuffer BUFFER = new UnsafeBuffer(BufferUtil.allocateDirectAligned(1024 * 256, 32));
	private ObjectMapper mapper = new ObjectMapper();
	private JsonFactory f = mapper.getFactory();

	private Publication publication;

	private static PTVerifyResultPublisher _instance = new PTVerifyResultPublisher();

	private PTVerifyResultPublisher() {
		initAeron();
	}

	public static synchronized PTVerifyResultPublisher getInstance() {
		if (_instance == null)
			_instance = new PTVerifyResultPublisher();
		return _instance;
	}


	private void initAeron() {

		final Aeron.Context ctx = new Aeron.Context();

		// Connect a new Aeron instance to the media driver and create a
		// publication on
		// the given channel and stream ID.
		// The Aeron and Publication classes implement "AutoCloseable" and will
		// automatically
		// clean up resources when this try block is finished
		try {
			final Aeron aeron = Aeron.connect(ctx);
			publication = aeron.addPublication(CHANNEL, STREAM_ID);
			log.debug("PTVerifyResultPublisher connected! CHANNEL = " + CHANNEL +" STREAM_ID = "+STREAM_ID);


		} catch (Exception ex) {

		}

	}
	
	
	
	public boolean publishResult(PITVerifyData data){
		if (data == null)
			return false;
		
		byte[] buf = serialObjToBytes(data);
		if (buf == null)
			return false;
		
		BUFFER.putBytes(0, buf);
		final long result = publication.offer(BUFFER, 0, buf.length);
		
		if (result < 0L) {
			if (result == Publication.BACK_PRESSURED) {
				log.error("  Offer failed due to back pressure");
			} else if (result == Publication.NOT_CONNECTED) {
				log.error("  Offer failed because publisher is not yet connected to subscriber");
			} else if (result == Publication.ADMIN_ACTION) {
				log.error("  Offer failed because of an administration action in the system");
			} else if (result == Publication.CLOSED) {
				log.error("  Offer failed publication is closed");
			} else {
				log.error("  Offer failed due to unknown reason");
			}
			return false;
		} else {
			log.debug("FaceVerifyResult has sended!fd data=="+data+", data length=" + buf.length);
			return true;
		}
	}


	private byte[] serialObjToBytes(Object o) {
		byte[] buf = null;
		ByteArrayOutputStream bos = null;
		ObjectOutputStream oos = null;
		try {
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(o);
			buf = bos.toByteArray();
			if (oos != null) {
				oos.close();
				oos = null;
			}
			if (bos != null) {
				bos.close();
				bos = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("serialObjToBytes", e);
		} finally {
			try {
				if (oos != null) {
					oos.close();
					oos = null;
				}
				if (bos != null) {
					bos.close();
					bos = null;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.error("serialObjToBytes", e);
			}
		}

		return buf;
	}
	
	private String OutputEventToJson(Object outputEvent) throws JsonProcessingException {
		return mapper.writeValueAsString(outputEvent);
	}

	public static void main(String[] args) {

		for (int i = 0; i < 1; i++) {
			PITVerifyData d = new PITVerifyData();
			d.setIdNo("1234567890");
			IDCard c1 = createIDCard("C:/pitchecking/B1.jpg");
			d.setFaceImg(c1.getManualImageBytes());
			d.setIdCardImg(c1.getManualImageBytes());
			d.setTicket(new Ticket());
			PTVerifyResultPublisher.getInstance().publishResult(d);
		}

	}
	
	private static IDCard createIDCard(String fn) {
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
