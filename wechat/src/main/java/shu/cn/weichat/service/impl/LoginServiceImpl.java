package shu.cn.weichat.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.regex.Matcher;

import lombok.extern.log4j.Log4j2;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.nlpcn.commons.lang.util.StringUtil;
import org.w3c.dom.Document;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import shu.cn.weichat.api.MessageTools;
import shu.cn.weichat.core.Core;
import shu.cn.weichat.core.MsgCenter;
import shu.cn.weichat.service.ILoginService;
import shu.cn.weichat.utils.*;
import shu.cn.weichat.utils.enums.ReplyMsgTypeEnum;
import shu.cn.weichat.utils.enums.ResultEnum;
import cn.zhouyafeng.itchat4j.utils.enums.RetCodeEnum;
import shu.cn.weichat.utils.enums.StorageLoginInfoEnum;
import shu.cn.weichat.utils.enums.URLEnum;
import shu.cn.weichat.utils.enums.parameters.BaseParaEnum;
import shu.cn.weichat.utils.enums.parameters.LoginParaEnum;
import shu.cn.weichat.utils.enums.parameters.StatusNotifyParaEnum;
import shu.cn.weichat.utils.enums.parameters.UUIDParaEnum;
import shu.cn.weichat.utils.tools.CommonTools;

/**
 * 登陆服务实现类
 *
 * @author SXS
 * @date 创建时间：2017年5月13日 上午12:09:35
 * @version 1.1
 *
 */
@Log4j2
public class LoginServiceImpl implements ILoginService {

	private Core core = Core.getInstance();
	private MyHttpClient httpClient = core.getMyHttpClient();

	private MyHttpClient myHttpClient = core.getMyHttpClient();
	public static ILoginService loginService ;
	public LoginServiceImpl() {

	}

	@Override
	public boolean login() {

		boolean isLogin = false;
		// 组装参数和URL
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		params.add(new BasicNameValuePair(LoginParaEnum.LOGIN_ICON.para(), LoginParaEnum.LOGIN_ICON.value()));
		params.add(new BasicNameValuePair(LoginParaEnum.UUID.para(), core.getUuid()));
		params.add(new BasicNameValuePair(LoginParaEnum.TIP.para(), LoginParaEnum.TIP.value()));

		// long time = 4000;
		while (!isLogin) {
			// SleepUtils.sleep(time += 1000);
			long millis = System.currentTimeMillis();
			params.add(new BasicNameValuePair(LoginParaEnum.R.para(), String.valueOf(millis / 1579L)));
			params.add(new BasicNameValuePair(LoginParaEnum._.para(), String.valueOf(millis)));
			HttpEntity entity = httpClient.doGet(URLEnum.LOGIN_URL.getUrl(), params, true, null);

			try {
				String result = EntityUtils.toString(entity);
				String status = checklogin(result);

				if (ResultEnum.SUCCESS.getCode().equals(status)) {
					processLoginInfo(result); // 处理结果
					isLogin = true;
					core.setAlive(isLogin);
					break;
				}
				if (ResultEnum.WAIT_CONFIRM.getCode().equals(status)) {
					log.info("请点击微信确认按钮，进行登陆");
				}

			} catch (Exception e) {
				log.error("微信登陆异常！", e);
			}
		}
		return isLogin;
	}

	@Override
	public String getUuid() {
		// 组装参数和URL
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		params.add(new BasicNameValuePair(UUIDParaEnum.APP_ID.para(), UUIDParaEnum.APP_ID.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum.FUN.para(), UUIDParaEnum.FUN.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum.LANG.para(), UUIDParaEnum.LANG.value()));
		params.add(new BasicNameValuePair(UUIDParaEnum._.para(), String.valueOf(System.currentTimeMillis())));

		HttpEntity entity = httpClient.doGet(URLEnum.UUID_URL.getUrl(), params, true, null);

		try {
			String result = EntityUtils.toString(entity);
			String regEx = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\";";
			Matcher matcher = CommonTools.getMatcher(regEx, result);
			if (matcher.find()) {
				if ((ResultEnum.SUCCESS.getCode().equals(matcher.group(1)))) {
					core.setUuid(matcher.group(2));
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return core.getUuid();
	}

	@Override
	public boolean getQR(String qrPath) {
		qrPath = qrPath + File.separator + "QR.jpg";
		String qrUrl = URLEnum.QRCODE_URL.getUrl() + core.getUuid();
		HttpEntity entity = myHttpClient.doGet(qrUrl, null, true, null);
		try {
			OutputStream out = new FileOutputStream(qrPath);
			byte[] bytes = EntityUtils.toByteArray(entity);
			out.write(bytes);
			out.flush();
			out.close();
			try {
				CommonTools.printQr(qrPath); // 打开登陆二维码图片
			} catch (Exception e) {
				log.info(e.getMessage());
			}
			String qrUrl2 = URLEnum.cAPI_qrcode.getUrl() + core.getUuid();
			String qrString= QRterminal.getQr(qrUrl2);
			log.error("\n"+qrString);
		} catch (Exception e) {
			log.info(e.getMessage());
			return false;
		}

		return true;
	}

	@Override
	public boolean webWxInit() {
		core.setAlive(true);
		core.setLastNormalRetcodeTime(System.currentTimeMillis());
		// 组装请求URL和参数
		String url = String.format(URLEnum.INIT_URL.getUrl(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.url.getKey()),
				String.valueOf(System.currentTimeMillis() / 3158L),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.getParamMap();

		// 请求初始化接口
		HttpEntity entity = httpClient.doPost(url, JSON.toJSONString(paramMap));
		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(result);

			JSONObject user = obj.getJSONObject(StorageLoginInfoEnum.User.getKey());
			JSONObject syncKey = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey());

			core.getLoginInfoMap().put(StorageLoginInfoEnum.InviteStartCount.getKey(),
					obj.getInteger(StorageLoginInfoEnum.InviteStartCount.getKey()));
			core.getLoginInfoMap().put(StorageLoginInfoEnum.SyncKey.getKey(), syncKey);

			JSONArray syncArray = syncKey.getJSONArray("List");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < syncArray.size(); i++) {
				sb.append(syncArray.getJSONObject(i).getString("Key") + "_"
						+ syncArray.getJSONObject(i).getString("Val") + "|");
			}
			// 1_661706053|2_661706420|3_661706415|1000_1494151022|
			String synckey = sb.toString();

			// 1_661706053|2_661706420|3_661706415|1000_1494151022
			core.getLoginInfoMap().put(StorageLoginInfoEnum.synckey.getKey(), synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			core.setUserName(user.getString("UserName"));
			core.setNickName(user.getString("NickName"));
			core.setUserSelf(obj.getJSONObject("User"));

			String chatSet = obj.getString("ChatSet");
			String[] chatSetArray = chatSet.split(",");
			for (int i = 0; i < chatSetArray.length; i++) {
				if (chatSetArray[i].indexOf("@@") != -1) {
					// 更新GroupIdList
					core.getGroupIdSet().add(chatSetArray[i]); //
				}
			}
			// JSONArray contactListArray = obj.getJSONArray("ContactList");
			// for (int i = 0; i < contactListArray.size(); i++) {
			// JSONObject o = contactListArray.getJSONObject(i);
			// if (o.getString("UserName").indexOf("@@") != -1) {
			// core.getGroupIdList().add(o.getString("UserName")); //
			// // 更新GroupIdList
			// core.getGroupList().add(o); // 更新GroupList
			// core.getGroupNickNameList().add(o.getString("NickName"));
			// }
			// }
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public void wxStatusNotify() {
		// 组装请求URL和参数
		String url = String.format(URLEnum.STATUS_NOTIFY_URL.getUrl(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.getParamMap();
		paramMap.put(StatusNotifyParaEnum.CODE.para(), StatusNotifyParaEnum.CODE.value());
		paramMap.put(StatusNotifyParaEnum.FROM_USERNAME.para(), core.getUserName());
		paramMap.put(StatusNotifyParaEnum.TO_USERNAME.para(), core.getUserName());
		paramMap.put(StatusNotifyParaEnum.CLIENT_MSG_ID.para(), System.currentTimeMillis());
		String paramStr = JSON.toJSONString(paramMap);

		try {
			HttpEntity entity = httpClient.doPost(url, paramStr);
			EntityUtils.toString(entity, Consts.UTF_8);
		} catch (Exception e) {
			log.error("微信状态通知接口失败！", e);
		}

	}

	@Override
	public void startReceiving() {
		core.setAlive(true);
		Runnable runnable = new Runnable() {
			int retryCount = 0;

			@Override
			public void run() {
				while (core.isAlive()) {
					try {
						Map<String, String> resultMap = syncCheck();
//						log.info(JSONObject.toJSONString(resultMap));
						String retcode = resultMap.get("retcode");
						String selector = resultMap.get("selector");
						if (retcode.equals(RetCodeEnum.UNKOWN.getCode())) {
							//好像搜狗输入法按语音键盘松手会触发
							//log.info(RetCodeEnum.UNKOWN.getType());
							continue;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OUT.getCode())) { // 退出
							log.info(RetCodeEnum.LOGIN_OUT.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OTHERWHERE.getCode())) { // 其它地方登陆
							log.info(RetCodeEnum.LOGIN_OTHERWHERE.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.MOBILE_LOGIN_OUT.getCode())) { // 移动端退出
							log.info(RetCodeEnum.MOBILE_LOGIN_OUT.getType());
							break;
						} else if (retcode.equals(RetCodeEnum.NORMAL.getCode())) {
							core.setLastNormalRetcodeTime(System.currentTimeMillis()); // 最后收到正常报文时间
							JSONObject msgObj = webWxSync();
							if (selector.equals("2")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										MsgCenter.produceMsg(msgList);
									} catch (Exception e) {
										e.printStackTrace();
										log.info(e.getMessage());
									}
								}
							} else if (selector.equals("7")) {
								webWxSync();
							} else if (selector.equals("4")) {
								continue;
							} else if (selector.equals("3")) {
								continue;
							} else if (selector.equals("6")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										JSONArray modContactList = msgObj.getJSONArray("ModContactList"); // 存在删除或者新增的好友信息
										MsgCenter.produceMsg(msgList);
										for (int j = 0; j < msgList.size(); j++) {
											JSONObject userInfo = modContactList.getJSONObject(j);
											// 存在主动加好友之后的同步联系人到本地
											core.getContactMap().put(userInfo.getString("UserName"),userInfo);
										}
									} catch (Exception e) {
										log.info(e.getMessage());
									}
								}

							}
						} else {
							JSONObject obj = webWxSync();
						}
					} catch (Exception e) {
						log.info(e.getMessage());
						retryCount += 1;
						if (core.getReceivingRetryCount() < retryCount) {
							core.setAlive(false);
						} else {
							SleepUtils.sleep(1000);
						}
					}

				}
			}
		};
		for (int i = 0; i < 1; i++) {
			new Thread(runnable,"ReceiveThread "+i).start();
		}

	}

	@Override
	public void webWxGetContact() {
		String url = String.format(URLEnum.WEB_WX_GET_CONTACT.getUrl(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.url.getKey()));
		Map<String, Object> paramMap = core.getParamMap();
		HttpEntity entity = httpClient.doPost(url, JSON.toJSONString(paramMap));

		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject fullFriendsJsonList = JSON.parseObject(result);
			// 查看seq是否为0，0表示好友列表已全部获取完毕，若大于0，则表示好友列表未获取完毕，当前的字节数（断点续传）
			long seq = 0;
			long currentTime = 0L;
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			if (fullFriendsJsonList.get("Seq") != null) {
				seq = fullFriendsJsonList.getLong("Seq");
				currentTime = new Date().getTime();
			}
			core.setMemberCount(fullFriendsJsonList.getInteger(StorageLoginInfoEnum.MemberCount.getKey()));
			JSONArray member = fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey());
			// 循环获取seq直到为0，即获取全部好友列表 ==0：好友获取完毕 >0：好友未获取完毕，此时seq为已获取的字节数
			while (seq > 0) {
				// 设置seq传参
				params.add(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.add(new BasicNameValuePair("seq", String.valueOf(seq)));
				entity = httpClient.doGet(url, params, false, null);

				params.remove(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.remove(new BasicNameValuePair("seq", String.valueOf(seq)));

				result = EntityUtils.toString(entity, Consts.UTF_8);
				fullFriendsJsonList = JSON.parseObject(result);

				if (fullFriendsJsonList.get("Seq") != null) {
					seq = fullFriendsJsonList.getLong("Seq");
					currentTime = new Date().getTime();
				}

				// 累加好友列表
				member.addAll(fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey()));
			}
			core.setMemberCount(member.size());
			for (Iterator<?> iterator = member.iterator(); iterator.hasNext();) {
				JSONObject o = (JSONObject) iterator.next();
				String userName = o.getString("UserName");
				if ((o.getInteger("VerifyFlag") & 8) != 0) {
					// 公众号/服务号
					put(core.getPublicUsersMap(),userName,o,"公众号/服务号");
				} else if (Config.API_SPECIAL_USER.contains(userName)) {
					// 特殊账号
					put(core.getSpecialUsersMap(),userName,o,"特殊账号");
				} else if (userName.indexOf("@@") != -1) {

					// 群聊
					if (!core.getGroupIdSet().contains(userName)) {
						log.info("新增群聊：{}",userName);
						core.getGroupNickNameSet().add(userName);
						core.getGroupIdSet().add(userName);
						core.getGroupMap().put(userName,o);
					}
				} else if (userName.equals(core.getUserSelf().getString("UserName"))) {
					// 自己
					core.getContactMap().remove(userName);
				} else {
					// 普通联系人
					put(core.getContactMap(),userName,o,"普通联系人");
				}
			}
			return;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return;
	}

	@Override
	public void WebWxBatchGetContact() {
		String url = String.format(URLEnum.WEB_WX_BATCH_GET_CONTACT.getUrl(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.url.getKey()), new Date().getTime(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.getParamMap();
		paramMap.put("Count", core.getGroupIdSet().size());
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		for (String s : core.getGroupIdSet()) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("UserName", s);
			map.put("EncryChatRoomId", "");
			list.add(map);
		}
		paramMap.put("List", list);
		HttpEntity entity = httpClient.doPost(url, JSON.toJSONString(paramMap));
		try {
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			JSONArray contactList = obj.getJSONArray("ContactList");
			for (int i = 0; i < contactList.size(); i++) { // 群好友
				if (contactList.getJSONObject(i).getString("UserName").indexOf("@@") > -1) {
					// 群
					// 更新群昵称列表
					core.getGroupNickNameSet().add(contactList.getJSONObject(i).getString("NickName"));
					// 更新群信息（所有）列表
					put(core.getGroupMap(),contactList.getJSONObject(i).getString("UserName"),contactList.getJSONObject(i),"群聊");
	/*				if(core.getGroupMap().put(contactList.getJSONObject(i).getString("UserName"),contactList.getJSONObject(i))==null){
						log.info("新增群：{}",contactList.getJSONObject(i));
					}*/
					// 更新群成员Map
					//put(core.getGroupMemberMap(),contactList.getJSONObject(i).getString("UserName"),contactList.getJSONObject(i).getJSONArray("MemberList"),"群成员");
					if(core.getGroupMemberMap().put(contactList.getJSONObject(i).getString("UserName"),
							contactList.getJSONObject(i).getJSONArray("MemberList")) != null){
//						int memberListOld = core.getGroupMemberMap().get(contactList.getJSONObject(i).getString("UserName")).size();
//						int memberListNew = contactList.getJSONObject(i).getJSONArray("MemberList").size();
//						if ()
//						log.info("新增群成员：{}-{}",contactList.getJSONObject(i).getString("NickName"),contactList.getJSONObject(i));
					}
				}
			}
//			log.info("core.getGroupNickNameList()："+core.getGroupNickNameSet().size());
//			log.info("core.getGroupList()："+core.getGroupMap().size());
//			log.info("core.getGroupMemeberMap()："+core.getGroupMemberMap().size());
		} catch (Exception e) {
			log.info(e.getMessage());
		}
	}

	/**
	 * 检查登陆状态
	 *
	 * @param result
	 * @return
	 */
	public String checklogin(String result) {
		String regEx = "window.code=(\\d+)";
		Matcher matcher = CommonTools.getMatcher(regEx, result);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * 处理登陆信息
	 *
	 * @author SXS
	 * @date 2017年4月9日 下午12:16:26
	 * @param loginContent
	 */
	private void processLoginInfo(String loginContent) {
		String regEx = "window.redirect_uri=\"(\\S+)\";";
		Matcher matcher = CommonTools.getMatcher(regEx, loginContent);
		if (matcher.find()) {
			String originalUrl = matcher.group(1);
			String url = originalUrl.substring(0, originalUrl.lastIndexOf('/')); // https://wx2.qq.com/cgi-bin/mmwebwx-bin
			core.getLoginInfoMap().put("url", url);
			Map<String, List<String>> possibleUrlMap = this.getPossibleUrlMap();
			Iterator<Entry<String, List<String>>> iterator = possibleUrlMap.entrySet().iterator();
			Map.Entry<String, List<String>> entry;
			String fileUrl;
			String syncUrl;
			while (iterator.hasNext()) {
				entry = iterator.next();
				String indexUrl = entry.getKey();
				fileUrl = "https://" + entry.getValue().get(0) + "/cgi-bin/mmwebwx-bin";
				syncUrl = "https://" + entry.getValue().get(1) + "/cgi-bin/mmwebwx-bin";
				if (core.getLoginInfoMap().get("url").toString().contains(indexUrl)) {
					core.setIndexUrl(indexUrl);
					core.getLoginInfoMap().put("fileUrl", fileUrl);
					core.getLoginInfoMap().put("syncUrl", syncUrl);
					break;
				}
			}
			if (core.getLoginInfoMap().get("fileUrl") == null && core.getLoginInfoMap().get("syncUrl") == null) {
				core.getLoginInfoMap().put("fileUrl", url);
				core.getLoginInfoMap().put("syncUrl", url);
			}
			core.getLoginInfoMap().put("deviceid", "e" + String.valueOf(new Random().nextLong()).substring(1, 16)); // 生成15位随机数
			core.getLoginInfoMap().put("BaseRequest", new ArrayList<String>());
			String text = "";

			try {
				HttpEntity entity = myHttpClient.doGet(originalUrl, null, false, null);
				text = EntityUtils.toString(entity);
			} catch (Exception e) {
				log.info(e.getMessage());
				return;
			}
			//add by 默非默 2017-08-01 22:28:09
			//如果登录被禁止时，则登录返回的message内容不为空，下面代码则判断登录内容是否为空，不为空则退出程序
			String msg = getLoginMessage(text);
			if (!"".equals(msg)){
				log.info(msg);
				System.exit(0);
			}
			Document doc = CommonTools.xmlParser(text);
			if (doc != null) {
				core.getLoginInfoMap().put(StorageLoginInfoEnum.skey.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.skey.getKey()).item(0).getFirstChild()
								.getNodeValue());
				core.getLoginInfoMap().put(StorageLoginInfoEnum.wxsid.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.wxsid.getKey()).item(0).getFirstChild()
								.getNodeValue());
				core.getLoginInfoMap().put(StorageLoginInfoEnum.wxuin.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.wxuin.getKey()).item(0).getFirstChild()
								.getNodeValue());
				core.getLoginInfoMap().put(StorageLoginInfoEnum.pass_ticket.getKey(),
						doc.getElementsByTagName(StorageLoginInfoEnum.pass_ticket.getKey()).item(0).getFirstChild()
								.getNodeValue());
			}

		}
	}

	private Map<String, List<String>> getPossibleUrlMap() {
		Map<String, List<String>> possibleUrlMap = new HashMap<String, List<String>>();
		possibleUrlMap.put("wx.qq.com", new ArrayList<String>() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx.qq.com");
				add("webpush.wx.qq.com");
			}
		});

		possibleUrlMap.put("wx2.qq.com", new ArrayList<String>() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx2.qq.com");
				add("webpush.wx2.qq.com");
			}
		});
		possibleUrlMap.put("wx8.qq.com", new ArrayList<String>() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx8.qq.com");
				add("webpush.wx8.qq.com");
			}
		});

		possibleUrlMap.put("web2.wechat.com", new ArrayList<String>() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web2.wechat.com");
				add("webpush.web2.wechat.com");
			}
		});
		possibleUrlMap.put("wechat.com", new ArrayList<String>() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web.wechat.com");
				add("webpush.web.wechat.com");
			}
		});
		return possibleUrlMap;
	}

	/**
	 * 同步消息 sync the messages
	 *
	 * @author SXS
	 * @date 2017年5月12日 上午12:24:55
	 * @return
	 */
	private JSONObject webWxSync() {
		JSONObject result = null;
		String url = String.format(URLEnum.WEB_WX_SYNC_URL.getUrl(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.url.getKey()),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.wxsid.getKey()),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.skey.getKey()),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.getParamMap();
		paramMap.put(StorageLoginInfoEnum.SyncKey.getKey(),
				core.getLoginInfoMap().get(StorageLoginInfoEnum.SyncKey.getKey()));
		paramMap.put("rr", -new Date().getTime() / 1000);
		String paramStr = JSON.toJSONString(paramMap);
		try {
			HttpEntity entity = myHttpClient.doPost(url, paramStr);
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			if (obj.getJSONObject("BaseResponse").getInteger("Ret") != 0) {
				result = null;
			} else {
				result = obj;
				core.getLoginInfoMap().put(StorageLoginInfoEnum.SyncKey.getKey(), obj.getJSONObject("SyncCheckKey"));
				JSONArray syncArray = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey()).getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_"
							+ syncArray.getJSONObject(i).getString("Val") + "|");
				}
				String synckey = sb.toString();
				core.getLoginInfoMap().put(StorageLoginInfoEnum.synckey.getKey(),
						synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			}
		} catch (Exception e) {
			log.info(e.getMessage());
		}
		return result;

	}

	/**
	 * 检查是否有新消息 check whether there's a message
	 *
	 * @author SXS
	 * @date 2017年4月16日 上午11:11:34
	 * @return
	 *
	 */
	private Map<String, String> syncCheck() {
		Map<String, String> resultMap = new HashMap<String, String>();
		// 组装请求URL和参数
		String url = core.getLoginInfoMap().get(StorageLoginInfoEnum.syncUrl.getKey()) + URLEnum.SYNC_CHECK_URL.getUrl();
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		for (BaseParaEnum baseRequest : BaseParaEnum.values()) {
			params.add(new BasicNameValuePair(baseRequest.para().toLowerCase(),
					core.getLoginInfoMap().get(baseRequest.value()).toString()));
		}
		params.add(new BasicNameValuePair("r", String.valueOf(new Date().getTime())));
		params.add(new BasicNameValuePair("synckey", (String) core.getLoginInfoMap().get("synckey")));
		params.add(new BasicNameValuePair("_", String.valueOf(new Date().getTime())));
		SleepUtils.sleep(7);
		try {
			HttpEntity entity = myHttpClient.doGet(url, params, true, null);
			if (entity == null) {
				resultMap.put("retcode", "9999");
				resultMap.put("selector", "9999");
				return resultMap;
			}
			String text = EntityUtils.toString(entity);
			String regEx = "window.synccheck=\\{retcode:\"(\\d+)\",selector:\"(\\d+)\"\\}";
			Matcher matcher = CommonTools.getMatcher(regEx, text);
			if (!matcher.find() || matcher.group(1).equals("2")) {
				log.info(String.format("Unexpected sync check result: %s", text));
			} else {
				resultMap.put("retcode", matcher.group(1));
				resultMap.put("selector", matcher.group(2));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultMap;
	}

	/**
	 * 解析登录返回的消息，如果成功登录，则message为空
	 * @param result
	 * @return
	 */
	public String getLoginMessage(String result){
		String[] strArr = result.split("<message>");
		String[] rs = strArr[1].split("</message>");
		if (rs!=null && rs.length>1) {
			return rs[0];
		}
		return "";
	}
	private void put(Map<String,JSONObject> map,String key,JSONObject newV,String tip){
		JSONObject oldV = map.get(key);
		String name =newV.getString("RemarkName");
		if (StringUtil.isBlank(name)) {
			name = newV.getString("NickName");
		}
		ArrayList<MessageTools.Result> results = new ArrayList<>();
		if (oldV != null){
			//存在key
			Map<String, Map<String, String>> differenceMap = JSONObjectUtil.getDifferenceMap(oldV, newV);
			if (differenceMap.size()>0){
				//Old与New存在差异
				log.info("{}（{}）属性更新：{}",tip,name,differenceMap);
				//发送消息
				results.add(MessageTools.Result.builder().msg(tip+"（"+name+"）属性更新："+differenceMap)
				.replyMsgTypeEnum(ReplyMsgTypeEnum.TEXT)
				.build());
				map.put(key,newV);
			}
		}else{
			log.info("新增{}（{}）：{}",tip,name,newV);
			//发送消息
			/*results.add(MessageTools.Result.builder().msg("新增"+tip+"（"+name+"）："+newV)
					.replyMsgTypeEnum(ReplyMsgTypeEnum.TEXT)
					.build());*/
			map.put(key,newV);
		}
		String userName = newV.getString("UserName");
		if (userName.startsWith("@@")){
			MessageTools.sendMsgByUserId(results,"filehelper");
		}else{
			MessageTools.sendMsgByUserId(results,userName);
		}

	}
/*	private void put(Map<String,JSONArray> map,String key,JSONArray value,String tip){
		JSONArray jsonArray = map.get(key);
		if (jsonArray != null){
			for (Object o : jsonArray) {
				JSONObject jsonObject =(JSONObject)o;
				//存在key
				Map<String, Map<String, String>> differenceMap = JSONObjectUtil.getDifferenceMap(jsonObject, value);
				if (differenceMap.size()>0){
					//Old与New存在差异
					log.info("{}（{}）属性更新：{}",tip,value.getString("NickName"),differenceMap);
					map.put(key,value);
				}
			}

		}else{
			log.info("新增{}（{}）：{}",tip,value.getString("NickName"),value);
			map.put(key,value);
		}
	}*/
}
