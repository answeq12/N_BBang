const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { GeoFire } = require("geofire-common");

try {
  admin.initializeApp();
  functions.logger.log("✅ Firebase Admin SDK 초기화 성공.");
} catch (error) {
  functions.logger.error("❌ Firebase Admin SDK 초기화 실패:", error);
}

const db = admin.firestore();
setGlobalOptions({ region: "asia-northeast3" });

exports.sendKeywordNotification = onDocumentCreated("posts/{postId}", async (event) => {
    const postId = event.params.postId;
    functions.logger.log(`--- [시작] 함수 실행 (Post ID: ${postId}) ---`);

    const snap = event.data;
    if (!snap) {
      functions.logger.warn(`[종료] 이벤트에 데이터가 없습니다.`);
      return;
    }

    const newPost = snap.data();
    const { title: postTitle, content: postContent, meetingLocation: postLocation } = newPost;

    if (!postLocation) {
        functions.logger.warn(`[종료] 게시글 ID ${postId}에 위치 정보(meetingLocation)가 없습니다.`);
        return;
    }

    const usersKeywordsSnapshot = await db.collection("user_keywords").get();
    if (usersKeywordsSnapshot.empty) {
        functions.logger.log("[종료] 키워드를 설정한 사용자가 없습니다.");
        return;
    }

    const notificationTasks = [];

    for (const userKeywordDoc of usersKeywordsSnapshot.docs) {
        const userId = userKeywordDoc.id;
        const keywordData = userKeywordDoc.data();
        const { keywords: keywordsMap, fcmToken } = keywordData;

        if (!keywordsMap || !fcmToken) {
            continue;
        }

        // 1. 'users' 컬렉션에서 사용자의 프로필 정보를 가져옵니다.
        const userProfileRef = db.collection("users").doc(userId);
        const userProfileSnap = await userProfileRef.get();

        if (!userProfileSnap.exists) {
            functions.logger.log(`[패스] 사용자 ${userId}: users 컬렉션에 프로필이 없습니다.`);
            continue;
        }

        // 2. 프로필에서 '인증된 동네 위치'인 GeoPoint를 가져옵니다.
        //    이 필드 이름은 안드로이드 앱에서 저장하는 이름과 반드시 같아야 합니다.
        const certifiedLocation = userProfileSnap.data().certifiedLocation;

        if (!certifiedLocation) {
            functions.logger.log(`[패스] 사용자 ${userId}: 프로필에 인증된 위치 좌표(certifiedLocation)가 없습니다.`);
            continue;
        }

        for (const keyword in keywordsMap) {
            if (postTitle.includes(keyword) || postContent.includes(keyword)) {
                const alertDistanceKm = keywordsMap[keyword];

                const distanceInMeters = GeoFire.distanceBetween(
                    [postLocation.latitude, postLocation.longitude],
                    [certifiedLocation.latitude, certifiedLocation.longitude]
                );
                const distanceInKm = (distanceInMeters / 1000).toFixed(2);

                functions.logger.log(`[정보] 사용자 ${userId}: 설정 거리(${alertDistanceKm}km), 실제 거리(${distanceInKm}km)`);

                if (distanceInKm <= alertDistanceKm) {
                    // ... (알림 전송 로직)
                }
            }
        }
    }

    return Promise.all(notificationTasks);
});
