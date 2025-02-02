package cn.shu.wechat.enums;

/**
 * 消息检测接口返回Code码
 */
public enum SyncCheckRetCodeEnum {

    SUCCESS("0", "成功"),
    LOGIN_OUT("1102", "退出"),
    LOGIN_OTHERWHERE("1101", "其它地方登陆"),
    MOBILE_LOGIN_OUT("1102", "移动端退出"),
    UNKOWN("9999", "未知"),
    TICKET_ERROR("-14", "ticket错误"),
    PARAM_ERROR("1", "传入参数错误"),
    NOT_LOGIN_WARN("1100", "未登录提示"),
    LOGIN_ENV_ERROR("1203", "当前登录环境异常，为了安全起见请不要在web端进行登录"),
    TOO_OFEN("1205", "操作频繁");;


    private final String code;
    private final String type;

    SyncCheckRetCodeEnum(String code, String type) {
        this.code = code;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

}
