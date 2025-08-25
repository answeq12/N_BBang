package com.bergi.nbang_v1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions // SetOptions 임포트 추가
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        var finalNotificationTitle: String? = null
        var finalNotificationBody: String? = null
        var postId: String? = null

        // 1. notification 페이로드에서 제목과 내용 가져오기 (우선순위 높음)
        remoteMessage.notification?.let {
            finalNotificationTitle = it.title
            finalNotificationBody = it.body
            Log.d(TAG, "Message Notification Title from notification payload: ${it.title}")
            Log.d(TAG, "Message Notification Body from notification payload: ${it.body}")
        }

        // 2. data 페이로드에서 postId 가져오기
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            postId = remoteMessage.data["postId"] // postId는 여기서 가져옵니다.
        }

        // 3. sendNotification 함수 호출
        // Cloud Function은 항상 notification.title, notification.body를 보내므로,
        // finalNotificationTitle과 finalNotificationBody가 null이 아닐 것으로 예상됩니다.
        // 만약 postId만 있고 title/body가 없는 data 메시지를 받는 경우도 처리하고 싶다면,
        // 아래 조건문이나 sendNotification 내부에서 기본값을 설정할 수 있습니다.
        if (finalNotificationTitle != null || finalNotificationBody != null || postId != null) {
            sendNotification(finalNotificationTitle, finalNotificationBody, postId)
        } else {
            Log.d(TAG, "No valid data to display notification.")
        }
    }


    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && token != null) {
            val userDocRef = Firebase.firestore.collection("users").document(currentUser.uid)
            // update 대신 set과 merge 옵션 사용
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "FCM token updated/set successfully for user ${currentUser.uid}") }
                .addOnFailureListener { e -> Log.w(TAG, "Error updating/setting FCM token for user ${currentUser.uid}", e) }
        } else {
            Log.d(TAG, "Cannot send token to server: currentUser is null or token is null.")
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     */
    private fun sendNotification(titleFromFcm: String?, bodyFromFcm: String?, postIdFromFcm: String?) {
        // 파라미터로 받은 title과 body를 사용하고, null이면 기본값 사용
        val title = titleFromFcm ?: getString(R.string.default_notification_title)
        val body = bodyFromFcm ?: getString(R.string.default_notification_body)
        val postId = postIdFromFcm

        Log.d(TAG, "Building notification with Title: $title, Body: $body, PostId: $postId")

        val intent: Intent
        if (postId != null) {
            intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("postId", postId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            // postId가 없는 경우 (예: 일반 공지), MainActivity 또는 다른 적절한 Activity로 이동
            intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // 고유한 request code 사용
            intent,
            pendingIntentFlags
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: 상태 표시줄용 아이콘으로 변경 (예: R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android Oreo (API 26) 이상에서는 알림 채널이 필요합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "기본 알림", // 사용자에게 표시될 채널 이름
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
