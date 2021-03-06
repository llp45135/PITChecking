package com.rxtec.pitchecking.gui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import java.awt.Font;
import javax.swing.JTabbedPane;
import javax.swing.JLayeredPane;
import javax.swing.JTextArea;
import javax.swing.JList;
import javax.swing.JSeparator;
import java.awt.Canvas;
import java.awt.Color;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Composite;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rxtec.pitchecking.AudioPlayTask;
import com.rxtec.pitchecking.Config;
import com.rxtec.pitchecking.FaceTrackingScreen;
import com.rxtec.pitchecking.device.DeviceConfig;
import com.rxtec.pitchecking.mbean.ProcessUtil;
import com.rxtec.pitchecking.mqtt.GatCtrlSenderBroker;
import com.rxtec.pitchecking.mqtt.MqttSenderBroker;
import com.rxtec.pitchecking.picheckingservice.FaceCheckingService;
import com.rxtec.pitchecking.picheckingservice.PITData;
import com.rxtec.pitchecking.utils.CommUtil;
import com.rxtec.pitchecking.utils.CalUtils;

import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import java.awt.CardLayout;

public class FaceCheckFrame extends JFrame implements ActionListener {
	private Logger log = LoggerFactory.getLogger("FaceScreenListener");
	private JPanel contentPane;
	private Timer timer = new Timer(1000, this);
	private JLabel label_title = new JLabel("\u8BF7\u5E73\u89C6\u6444\u50CF\u5934");
	private JPanel panel_title;
	private JPanel panel_bottom;
	private JPanel panel_center;
	private VideoPanel videoPanel = new VideoPanel(Config.FrameWidth, Config.FrameHeigh);

	private JPanel cameraPanel;
	private JPanel msgPanel;
	private JLabel msgTopLabel;
	private JLabel passImgLabel;
	private String displayMsg = "";

	private JLabel bootTimeLabel;

	int timeIntevel = Config.getInstance().getFaceCheckDelayTime();

	private int titleStrType = 0;
	private JLabel IPLabel;
	private JLabel trackVerLabel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					// 语音调用线程
					ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
					scheduler.scheduleWithFixedDelay(AudioPlayTask.getInstance(), 0, 100, TimeUnit.MILLISECONDS);

					FaceTrackingScreen.getInstance();
					FaceCheckFrame frame = new FaceCheckFrame();
					FaceTrackingScreen.getInstance().setFaceFrame(frame);
					FaceTrackingScreen.getInstance().initUI(0);
					frame.showDefaultContent();
					// CommUtil.sleep(3 * 1000);
					// frame.setVisible(true);
					// MqttSenderBroker.getInstance(DeviceConfig.GAT_MQ_Track_CLIENT
					// + Config.getInstance().getCameraNum())
					// .setFaceScreenDisplayTimeout(5);
					// MqttSenderBroker.getInstance(DeviceConfig.GAT_MQ_Track_CLIENT
					// + Config.getInstance().getCameraNum())
					// .setFaceScreenDisplay("人证核验失败#请从侧门离开");
					// MqttSenderBroker.getInstance().setFaceScreenDisplay("人脸识别失败#请从侧门离开");
					frame.showFaceDisplayFromTK();
					// AudioPlayTask.getInstance().start(DeviceConfig.takeTicketFlag);
					GatCtrlSenderBroker.getInstance(DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())
							.sendDoorCmd(ProcessUtil.createTkEventJson(DeviceConfig.AudioTakeTicketFlag, "FaceAudio"));
					// frame.showBeginCheckFaceContent();
					// frame.showFaceCheckPassContent();
					// frame.showCheckFailedContent();
					// frame.showDefaultContent();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public VideoPanel getVideoPanel() {
		return videoPanel;
	}

	/**
	 * Create the frame.
	 */
	public FaceCheckFrame() {
		setResizable(false);
		// 取得屏幕大小
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Rectangle bounds = new Rectangle(screenSize);

		setBounds(new Rectangle(0, 0, 1024, 768));
		setMinimumSize(new Dimension(1024, 768));
		setMaximumSize(new Dimension(1024, 768));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		contentPane = new JPanel();
		setContentPane(contentPane);
		contentPane.setLayout(new CardLayout(0, 0));

		msgPanel = new JPanel() {
			private static final long serialVersionUID = -3812942899603254185L;
			private Image localImg;
			private MediaTracker mt;
			private int w;
			private int h;

			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				try {
					g.drawImage(this.getBgImage(DeviceConfig.faceBgImgPath), 0, 0, null);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // 画窗口背景图
			}

			/**
			 *
			 * @param name
			 * @param imagePath
			 * @return
			 */
			public Image getBgImage(String imagePath) {
				Image bgImage = null;
				try {
					localImg = new ImageIcon(imagePath).getImage(); // 读取本地图片
					mt = new MediaTracker(this);// 为此按钮添加媒体跟踪器
					mt.addImage(localImg, 0);// 在跟踪器添加图片，下标为0
					mt.waitForAll(); // 等待加载
					w = localImg.getWidth(this);// 读取图片长度
					h = localImg.getHeight(this);// 读取图片宽度

					GraphicsConfiguration gc = new JFrame().getGraphicsConfiguration();
					// 本地图形设备
					bgImage = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
					// 建立透明画布
					Graphics2D g = (Graphics2D) bgImage.getGraphics(); // 在画布上创建画笔

					Composite alpha = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Config.getInstance().getFaceFrameTransparency()); // 指定透明度为半透明90%
					g.setComposite(alpha);
					g.drawImage(localImg, 0, 0, this); // 注意是,将image画到g画笔所在的画布上
					g.setColor(Color.black);// 设置颜色为黑色
					g.dispose(); // 释放内存
				} catch (Exception e) {
					e.printStackTrace();
				}

				return bgImage;
			}

		};

		contentPane.add(msgPanel, "name_1726792116426379");
		msgPanel.setLayout(null);

		msgTopLabel = new JLabel("<html><div align='center'>人脸识别系统</div><div align='center'>启动中</div></html>");
		msgTopLabel.setBorder(new LineBorder(new Color(0, 0, 0)));
		msgTopLabel.setForeground(Color.YELLOW);
		msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));
		msgTopLabel.setHorizontalAlignment(SwingConstants.CENTER);
		msgTopLabel.setBounds(10, 104, 1004, 553);
		msgPanel.add(msgTopLabel);

		// passImgLabel = new JLabel("New label");
		// passImgLabel.setHorizontalAlignment(SwingConstants.CENTER);
		// passImgLabel.setBounds(10, 277, 183, 215);
		// msgPanel.add(passImgLabel);

		cameraPanel = new JPanel();
		contentPane.add(cameraPanel, "name_219670385833610");
		cameraPanel.setLayout(new BoxLayout(cameraPanel, BoxLayout.Y_AXIS));

		panel_center = new JPanel();
		panel_center.setMinimumSize(new Dimension(1024, 568));
		panel_center.setMaximumSize(new Dimension(1024, 568));
		panel_center.setLayout(new BoxLayout(panel_center, BoxLayout.Y_AXIS));

		// contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
		// panel_center.add(Box.createVerticalStrut(35));
		panel_center.add(Box.createVerticalStrut(0));
		videoPanel.setBorder(new LineBorder(Color.GREEN));
		videoPanel.setMinimumSize(new Dimension(Config.FrameWidth, Config.FrameHeigh));
		videoPanel.setMaximumSize(new Dimension(Config.FrameWidth, Config.FrameHeigh));

		panel_center.add(videoPanel);

		panel_title = new JPanel();
		panel_title.setBackground(null);
		panel_title.setMinimumSize(new Dimension(1024, 100));
		panel_title.setMaximumSize(new Dimension(1024, 100));
		panel_title.setLayout(null);
		label_title.setBounds(10, 5, 1004, 85);

		label_title.setHorizontalTextPosition(SwingConstants.CENTER);
		label_title.setHorizontalAlignment(SwingConstants.CENTER);
		label_title.setAlignmentX(Component.RIGHT_ALIGNMENT);
		panel_title.add(label_title);
		label_title.setFont(new Font("微软雅黑", Font.PLAIN, 60));

		panel_bottom = new JPanel();
		panel_bottom.setMinimumSize(new Dimension(1024, 100));
		panel_bottom.setMaximumSize(new Dimension(1024, 100));
		panel_bottom.setLayout(null);

		cameraPanel.add(panel_title);
		cameraPanel.add(panel_center);
		cameraPanel.add(panel_bottom);

		bootTimeLabel = new JLabel("启动时间:yyyy-MM-dd hh:mi:ss");
		bootTimeLabel.setForeground(Color.YELLOW);
		bootTimeLabel.setBorder(new LineBorder(new Color(0, 0, 0)));
		bootTimeLabel.setHorizontalAlignment(SwingConstants.CENTER);
		bootTimeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
		bootTimeLabel.setBounds(717, 56, 297, 34);
		panel_bottom.add(bootTimeLabel);

		IPLabel = new JLabel("启动时间:2016-09-29 10:45:42");
		IPLabel.setForeground(Color.YELLOW);
		IPLabel.setBorder(new LineBorder(new Color(0, 0, 0)));
		IPLabel.setHorizontalAlignment(SwingConstants.CENTER);
		IPLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
		IPLabel.setBounds(366, 56, 297, 34);
		panel_bottom.add(IPLabel);

		trackVerLabel = new JLabel("启动时间:2016-09-29 10:45:42");
		trackVerLabel.setForeground(Color.YELLOW);
		trackVerLabel.setHorizontalAlignment(SwingConstants.CENTER);
		trackVerLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
		trackVerLabel.setBorder(new LineBorder(new Color(0, 0, 0)));
		trackVerLabel.setBounds(10, 56, 297, 34);
		panel_bottom.add(trackVerLabel);

		bootTimeLabel.setText("启动时间：" + CalUtils.getStringDate());
		IPLabel.setText("本机IP：" + DeviceConfig.getInstance().getIpAddress());
		trackVerLabel.setText("软件版本：" + DeviceConfig.softVersion + DeviceConfig.getInstance().getCameraDirection());

		bootTimeLabel.setBorder(null);
		IPLabel.setBorder(null);
		trackVerLabel.setBorder(null);

		// showDefaultContent();

		// this.setLocationRelativeTo(null);
		// setUndecorated(true);
		if (!FaceCheckingService.getInstance().isFrontCamera()) { // 后置摄像头
			setAlwaysOnTop(true);
		} else {
			log.debug("自动将前置摄像头界面缩小至最小化");
			setUndecorated(true);
			// setDefaultLookAndFeelDecorated(true);
			com.sun.awt.AWTUtilities.setWindowOpacity(this, Config.getInstance().getFrontCameraWindowOpacity());
		}

	}

	// public void showIDCardImage(ImageIcon icon) {
	// //idCardImage.repaint();
	// //panel_idCardImage.repaint();
	// }

	public void showPassStatusImage(ImageIcon icon) {
		passImgLabel.setText("");
		// passImgLabel.setBorder(new MatteBorder(1, 1, 1, 1, (Color) new
		// Color(0, 0, 0)));
		passImgLabel.setIcon(icon);
	}

	/**
	 * 主控端通过调用dll接口方式传输
	 */
	public void showFaceDisplayFromTK() {
		timeIntevel = 0;

		// String pidName = "";
		// if (Config.getInstance().getFaceControlMode() == 1) {
		// pidName = DeviceConfig.GAT_MQ_Track_CLIENT +
		// Config.getInstance().getCameraNum();
		// } else {
		// pidName = DeviceConfig.GAT_MQ_Standalone_CLIENT;
		// }

		// String displayStr =
		// MqttSenderBroker.getInstance(pidName).getFaceScreenDisplay();
		String displayStr = DeviceConfig.getInstance().getFaceScreenDisplay();
		log.info("displayStr=="+displayStr);

		// this.displayMsg = displayStr.replace("#", "！");
		//
		// if (displayStr.indexOf("成功") != -1) {
		// panel_title.setBackground(Color.GREEN);
		// panel_bottom.setBackground(Color.GREEN);
		// label_title.setBackground(Color.GREEN);
		// label_title.setForeground(Color.BLACK);
		// } else {
		// panel_title.setBackground(Color.RED);
		// panel_bottom.setBackground(Color.RED);
		// label_title.setBackground(Color.RED);
		// label_title.setForeground(Color.BLACK);
		// }
		// label_title.setText(displayMsg);

		this.cameraPanel.setVisible(false);
		this.videoPanel.setVisible(false);

		msgPanel.setVisible(true);
		msgPanel.setBackground(null); // 把背景设置为空
		msgPanel.setOpaque(false); // 设置为透明
		msgTopLabel.setBorder(null);
		msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));

		if (displayStr.indexOf("成功") != -1 || displayStr.indexOf("请到") != -1) {
			// timeIntevel =
			// MqttSenderBroker.getInstance(pidName).getFaceScreenDisplayTimeout();//
			// 5;
			timeIntevel = DeviceConfig.getInstance().getFaceScreenDisplayTimeout();
			// //
			// 成功时的提示信息存在时间
			timeIntevel = 5;// 暂时设置为5s
		} else {
			// ImageIcon icon = new ImageIcon(DeviceConfig.forbidenImgPath);
			// this.showPassStatusImage(icon);
			// timeIntevel =
			// MqttSenderBroker.getInstance(pidName).getFaceScreenDisplayTimeout();
			timeIntevel = DeviceConfig.getInstance().getFaceScreenDisplayTimeout();
		}
		titleStrType = 4; // 4:覆盖一层panel 5：不覆盖

		if (displayStr.indexOf("成功") != -1) {
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 225));
			displayStr = "请通过";
		} else if (displayStr.indexOf("一候") != -1) {
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, Config.getInstance().getSuccessFontSize()));
			displayStr = "请到 1 候车室#候车";
		} else if (displayStr.indexOf("二候") != -1) {
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, Config.getInstance().getSuccessFontSize()));
			displayStr = "请到 2 候车室#候车";
		} else if (displayStr.indexOf("三候") != -1) {
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, Config.getInstance().getSuccessFontSize()));
			displayStr = "请到 3 候车室#候车";
		} else if (displayStr.indexOf("四候") != -1) {
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, Config.getInstance().getSuccessFontSize()));
			displayStr = "请到 4 候车室#候车";
		} else if (displayStr.indexOf("高速候车室") != -1) {   //普速场高速候车室
			msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, Config.getInstance().getSuccessFontSize()));
			displayStr = "请从 3 候车室#进入城际通道";
		}
		if (displayStr.indexOf("#") != -1) { // 由#号，分两行
			int kk = displayStr.indexOf("#");
			String displayMsg1 = displayStr.substring(0, kk);
			String displayMsg2 = displayStr.substring(kk + 1);
			this.displayMsg = "<html><div align='center'>" + displayMsg1 + "</div>" + "<div align='center'>" + displayMsg2 + "</div>";
			this.msgTopLabel.setText(displayMsg + "</html>");
		} else {
			this.displayMsg = "<html><div align='center'>" + displayStr + "</div>";
			this.msgTopLabel.setText(displayMsg + "</html>");
		}
	}

	/**
	 * 验证通过处理
	 */
	public void showFaceCheckPassContent() {
		timeIntevel = 0;

		// panel_title.setBackground(Color.GREEN);
		// panel_bottom.setBackground(Color.GREEN);
		// label_title.setBackground(Color.GREEN);
		//
		// label_title.setForeground(Color.BLACK);
		// label_title.setText("人脸识别成功！请通过");

		this.cameraPanel.setVisible(false);
		this.videoPanel.setVisible(false);

		msgPanel.setVisible(true);
		msgPanel.setBackground(null); // 把背景设置为空
		msgPanel.setOpaque(false); // 设置为透明
		msgTopLabel.setBorder(null);
		msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));

		timeIntevel = 3;
		titleStrType = 0;
		this.msgTopLabel.setText("<html><div align='center'>人脸识别成功</div><div align='center'>请通过</div><div align='center'>" + timeIntevel + "</div></html>");
	}

	/**
	 * 检脸失败处理
	 */
	public void showCheckFailedContent() {
		timeIntevel = 0;

		// panel_title.setBackground(Color.RED);
		// panel_bottom.setBackground(Color.RED);
		// label_title.setBackground(Color.RED);
		//
		// label_title.setForeground(Color.BLACK);
		// label_title.setText("人脸识别失败！请从侧门离开");
		// label_title.setBackground(null);
		// panel_bottom.setVisible(true);

		this.cameraPanel.setVisible(false);
		this.videoPanel.setVisible(false);

		msgPanel.setVisible(true);
		msgPanel.setBackground(null); // 把背景设置为空
		msgPanel.setOpaque(false); // 设置为透明
		msgTopLabel.setBorder(null);
		msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));

		timeIntevel = 5;
		titleStrType = 1;
		this.msgTopLabel.setText("<html><div align='center'>人脸识别失败</div><div align='center'>请从侧门离开</div><div align='center'>" + timeIntevel + "</div></html>");
	}

	/**
	 * 初始界面
	 */
	public void showDefaultContent() {
		// try {
		// Thread.sleep(Config.getInstance().getDefaultFaceCheckScreenDeley());
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		if (this.titleStrType != 2) { // 类型为开始检脸，那么结束时不播放引导语音
			String startPlayTime = CalUtils.getStringDateShort() + " " + "05:30:00";
			String endPlayTime = CalUtils.getStringDateShort() + " " + "23:30:00";
			if (CalUtils.isDateAfter(startPlayTime) && CalUtils.isDateBefore(endPlayTime)) {
				if (Config.getInstance().getIsPlayHelpAudio() == 1) {
					// 循环播放引导使用语音
					GatCtrlSenderBroker.getInstance(DeviceConfig.GAT_MQ_Track_CLIENT + Config.getInstance().getCameraNum())
							.sendDoorCmd(ProcessUtil.createTkEventJson(DeviceConfig.AudioUseHelpFlag, "FaceAudio"));
				}
			} else {
				log.debug("当前时间段不在" + startPlayTime + "--" + endPlayTime + "之间,不可以播放引导语音");
			}
		}

		this.msgPanel.setVisible(false);
		this.cameraPanel.setVisible(true);
		this.videoPanel.setVisible(true);

//		Color bgColor = new Color(0, 142, 240);
		Color bgColor = new Color(2, 99, 154);
		panel_title.setBackground(bgColor);
		panel_bottom.setBackground(bgColor);
		label_title.setBackground(bgColor);

		label_title.setForeground(Color.WHITE);
		label_title.setText("");

		timeIntevel = 0;
		timer.start();

	}

	/**
	 * 开始检脸
	 */
	public void showBeginCheckFaceContent() {
		// try {
		// FaceTrackingScreen.getInstance().initUI(DeviceConfig.getInstance().getFaceScreen());
		// FaceTrackingScreen.getInstance().repainFaceFrame();
		// } catch (Exception ex) {
		// log.error("showBeginCheckFaceContent:", ex);
		// }
		msgPanel.setVisible(false);
		this.cameraPanel.setVisible(true);
		this.videoPanel.setVisible(true);

		panel_title.setBackground(Color.ORANGE);
		panel_bottom.setBackground(Color.ORANGE);
		label_title.setBackground(Color.ORANGE);
		label_title.setForeground(Color.BLACK);
		label_title.setText("");

		timeIntevel = Config.getInstance().getFaceCheckDelayTime();
		this.titleStrType = 2;
		label_title.setText("请抬头看屏幕     " + timeIntevel);
	}

	// private void timeRefresh() {
	// String now = DateUtils.getStringDate();
	//// timelabel.setText(now);
	// }

	/**
	 * 执行Timer要执行的部分，
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		// timeRefresh();

		if (timeIntevel > 0) {
			if (this.titleStrType == 0) {
				// label_title.setText("人脸识别成功！请通过" + " " + (timeIntevel - 1));

				msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));
				msgTopLabel.setText("<html><div align='center'>人脸识别成功</div><div align='center'>请通过</div><div align='center'>" + (timeIntevel - 1) + "</di></html>");
			} else if (this.titleStrType == 1) {
				// label_title.setText("人脸识别失败！请从侧门离开" + " " + (timeIntevel -
				// 1));

				msgTopLabel.setFont(new Font("微软雅黑", Font.PLAIN, 150));
				msgTopLabel.setText("<html><div align='center'>人脸识别失败</div><div align='center'>请从侧门离开</div><div align='center'>" + (timeIntevel - 1) + "</di></html>");
			} else if (this.titleStrType == 4) {
				msgTopLabel.setText(displayMsg + "</html>");
			} else if (this.titleStrType == 5) {
				label_title.setText(displayMsg + "  " + (timeIntevel - 1));
				// label_title.setBackground(null);
			} else {
				label_title.setText("请抬头看屏幕     " + (timeIntevel - 1));
			}
		}
		if (timeIntevel == 0) {
			this.showDefaultContent();
		}

		if (timeIntevel-- < 0)
			timeIntevel = -1;
	}
}
