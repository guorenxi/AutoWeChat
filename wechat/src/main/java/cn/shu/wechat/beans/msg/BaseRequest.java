package cn.shu.wechat.beans.msg;

import cn.shu.wechat.core.Core;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * @作者 舒新胜
 * @项目 AutoWeChat
 * @创建时间 3/10/2021 2:30 PM
 */


public class BaseRequest {

    public String DeviceID = Core.getLoginInfoMap().get("pass_ticket").toString();

    public String Uin = Core.getLoginInfoMap().get("wxuin").toString();

    public String Skey = Core.getLoginInfoMap().get("skey").toString();

    public String Sid = Core.getLoginInfoMap().get("wxsid").toString();
}
