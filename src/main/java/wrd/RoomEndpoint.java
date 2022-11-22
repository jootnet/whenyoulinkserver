package wrd;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.alibaba.fastjson2.JSON;

@ServerEndpoint("/room")
public class RoomEndpoint {

	private static final String ID_KEY = "id";
	// 一些预设的返回字符串
	private static final String MID_SET_OK = "{\"code\":0}";
	private static final String MID_SET_FAILD_DUP = "{\"code\":-1,\"msg\":\"登录失败，ID重复。如果存在前次登录，可能需要退出后等待一分钟超时。\"}";
	private static final String SEND_FAILD_NLOGIN = "{\"code\":-2,\"msg\":\"发送数据失败，未登录。\"}";
	private static final String SEND_FAILD_NOTFOUND = "{\"code\":-3,\"msg\":\"发送数据失败，对方不在线。\"}";

	// id与会话的对应关系
	private static Map<String, Session> id2Session = new ConcurrentHashMap<>();

	@OnMessage
	public void onMessage(Session ses, String msg) throws IOException {
		var root = JSON.parseObject(msg);
		var peer_id = root.getString("peer_id");
		var me_id = root.getString("me_id");
		if (peer_id != null) {
			if (peer_id.isEmpty() || peer_id.isBlank())
				peer_id = null;
		}
		if (me_id != null) {
			if (me_id.isEmpty() || me_id.isBlank())
				me_id = null;
		}
		if (peer_id == null) {
			if (me_id == null) return;
			// 如果对方id为null，表示是设置（或续订）自身id
			String currentMid = (String) ses.getUserProperties().get(ID_KEY);
			if (me_id.equals(currentMid)) {
				ses.getBasicRemote().sendText(MID_SET_OK);
				return;
			}
			if (id2Session.putIfAbsent(me_id, ses) != null) {
				ses.getBasicRemote().sendText(MID_SET_FAILD_DUP);
				return;
			}
			if (currentMid != null) {
				id2Session.remove(currentMid);
			}
			ses.getUserProperties().put(ID_KEY, me_id);
			ses.getBasicRemote().sendText(MID_SET_OK);
		} else {
			if (!ses.getUserProperties().containsKey(ID_KEY)) {
				ses.getBasicRemote().sendText(SEND_FAILD_NLOGIN);
				return;
			}
			if (peer_id.equals(ses.getUserProperties().get(ID_KEY)))
				return;
			var peer = id2Session.get(peer_id);
			if (peer == null) {
				ses.getBasicRemote().sendText(SEND_FAILD_NOTFOUND);
			} else {
				var data = root;
				data.remove("me_id");
				data.put("peer_id", ses.getUserProperties().get(ID_KEY));
				peer.getBasicRemote().sendText(data.toString());
			}
		}
	}

	@OnOpen
	public void onOpen(Session ses) {
		// 一分钟超时
		ses.setMaxIdleTimeout(60 * 1000);
		// 收发文字的最大长度。默认的8192有点小，可能会不够存储较长的SDP
		ses.setMaxTextMessageBufferSize(65536);
	}

	@OnClose
	public void onClose(Session ses) {
		String mid = (String) ses.getUserProperties().get(ID_KEY);
		if (mid != null) {
			id2Session.remove(mid);
		}
		ses.getUserProperties().clear();
	}
}
