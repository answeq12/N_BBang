// functions/index.js

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

// 모든 함수에 대한 글로벌 옵션 설정 (리전: asia-northeast3)
setGlobalOptions({ region: "asia-northeast3" });

// 두 GeoPoint 간의 거리를 km로 계산하는 함수 (Haversine 공식)
function getDistanceFromLatLonInKm(lat1, lon1, lat2, lon2) {
  const R = 6371; // 지구 반지름 (km)
  const dLat = deg2rad(lat2 - lat1);
  const dLon = deg2rad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(deg2rad(lat1)) *
      Math.cos(deg2rad(lat2)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c_val = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const d = R * c_val;
  return d;
}

function deg2rad(deg) {
  return deg * (Math.PI / 180);
}

// Firestore 문서 생성 트리거
exports.sendKeywordNotification = onDocumentCreated(
  "posts/{postId}",
  async (event) => {
    const snap = event.data;
    if (!snap) {
      console.log("No data associated with the event.");
      return;
    }

    const newPost = snap.data();
    const postId = event.params.postId;

    if (
      !newPost ||
      typeof newPost.title !== "string" ||
      !newPost.meetingLocation ||
      typeof newPost.meetingLocation.latitude !== "number" ||
      typeof newPost.meetingLocation.longitude !== "number"
    ) {
      console.log(
        `Post ${postId} data invalid. Title: ${newPost.title}, MeetingLocation: ${JSON.stringify(
          newPost.meetingLocation
        )}`
      );
      return;
    }

    const postTitle = newPost.title.toLowerCase();
    const postDescription = (newPost.content || "").toLowerCase();
    const postLatitude = newPost.meetingLocation.latitude;
    const postLongitude = newPost.meetingLocation.longitude;

    console.log(
      `Processing post: ${postId}, Title: ${newPost.title}, Location: (${postLatitude}, ${postLongitude})`
    );

    try {
      const usersSnapshot = await db.collection("users").get();
      if (usersSnapshot.empty) {
        console.log("No users found.");
        return;
      }
      console.log(`Found ${usersSnapshot.size} users to check.`);

      const notificationPromises = [];

      usersSnapshot.forEach((userDoc) => {
        const userData = userDoc.data();
        const userId = userDoc.id;

        if (
          userData.fcmToken &&
          userData.notificationKeywords &&
          Array.isArray(userData.notificationKeywords) &&
          userData.notificationKeywords.length > 0 &&
          userData.locationPoint &&
          typeof userData.locationPoint.latitude === "number" &&
          typeof userData.locationPoint.longitude === "number"
        ) {
          const userKeywords = userData.notificationKeywords.map((kw) =>
            String(kw).toLowerCase()
          );
          const userLocation = userData.locationPoint;
          const userNotificationRadiusKm =
            typeof userData.notificationRadiusKm === "number" &&
            userData.notificationRadiusKm > 0
              ? userData.notificationRadiusKm
              : 5.0;

          const distanceKm = getDistanceFromLatLonInKm(
            postLatitude,
            postLongitude,
            userLocation.latitude,
            userLocation.longitude
          );

          if (distanceKm <= userNotificationRadiusKm) {
            let matchedKeyword = null;
            for (const keyword of userKeywords) {
              if (
                keyword &&
                (postTitle.includes(keyword) ||
                  postDescription.includes(keyword))
              ) {
                matchedKeyword = keyword;
                break;
              }
            }

            if (matchedKeyword) {
              console.log(
                `Match for user ${userId}, keyword: "${matchedKeyword}", distance: ${distanceKm.toFixed(
                  2
                )}km`
              );

              const notificationTitleText =
                newPost.title.length > 30
                  ? `${newPost.title.substring(0, 27)}...`
                  : newPost.title;

              // ✅ 최신 send() 방식
              const message = {
                token: userData.fcmToken,
                notification: {
                  title: `"${matchedKeyword}" 키워드 알림!`,
                  body: notificationTitleText,
                },
                data: {
                  postId: postId,
                },
              };

              notificationPromises.push(
                admin
                  .messaging()
                  .send(message)
                  .then((response) => {
                    console.log(
                      `Successfully sent message to user ${userId}: ${response}`
                    );
                    return response;
                  })
                  .catch((error) => {
                    console.error(
                      `Error sending notification to user ${userId} for token ${userData.fcmToken}`,
                      error
                    );
                    if (
                      error.code ===
                        "messaging/registration-token-not-registered" ||
                      error.code === "messaging/invalid-argument"
                    ) {
                      console.log(
                        `Removing invalid FCM token for user ${userId}: ${userData.fcmToken}`
                      );
                      return db
                        .collection("users")
                        .doc(userId)
                        .update({
                          fcmToken: admin.firestore.FieldValue.delete(),
                        });
                    }
                    return null;
                  })
              );
            }
          }
        }
      });

      if (notificationPromises.length > 0) {
        return Promise.all(notificationPromises.filter((p) => p !== null))
          .then((results) => {
            console.log(
              `Notifications processed. Total attempts: ${results.length}`
            );
          })
          .catch((error) => {
            console.error("Error processing notifications:", error);
          });
      } else {
        console.log("No users to notify.");
        return null;
      }
    } catch (error) {
      console.error("Error fetching users or sending notifications:", error);
      return null;
    }
  }
);
