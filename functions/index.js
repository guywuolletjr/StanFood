const admin = require('firebase-admin');
const functions = require('firebase-functions');
const moment = require('moment');

admin.initializeApp(functions.config().firebase);

/**
 * Compares two times of format h:mm
 * Returns -1 if a < b, 0 if a = b, and 1 if a > b
 */
function compare(a, b) {
  let a_hour = Number(a.split(':')[0]);
  let a_min = Number(a.split(':')[1]);
  let b_hour = Number(b.split(':')[0]);
  let b_min = Number(b.split(':')[1]);

  if (a_hour === b_hour && a_min === b_min) {
    return 0; // a = b
  }
  if (a_hour > b_hour || (a_hour === b_hour && a_min > b_min)) {
    return 1; // a > b
  }
  return -1; // a < b
}

/**
 * Given three times of format h:mm, where lowerLimit < upperLimit,
 * returns true if value is in [lowerLimit, upperLimit] inclusive, false otherwise
 */
function isBetween(value, lowerLimit, upperLimit) {
  return compare(value, lowerLimit) >= 0 && compare(value, upperLimit) <= 0;
}

exports.checkPinEvents = functions.https.onRequest((req, res) => {

  //create database refs
  var pinsRef = admin.database().ref('/pins');
  var eventsRef = admin.database().ref('/events');
  var foodRef = admin.database().ref('/food');

  // initialize storage bucket
  var bucket = admin.storage().bucket();

  eventsRef.once('value', (snapshot) => {
  	snapshot.forEach((childSnapshot) => {
  		var data = childSnapshot.val();
  		try {
  			var timeStart = data.timeStart;
  			var duration = data.duration;
  			var curTime = new Date().getTime();

  			// if event is expired
  			if(timeStart + duration < curTime){
  				var eventKey = childSnapshot.key;
  				var pinId = data.pinId;

          // removed food associated with expired event
          foodRef.orderByChild('eventId').equalTo(eventKey).once('value').then((foodSnapshot) => {
            return foodSnapshot.forEach((foodChildSnapshot) => {
              var foodKey = foodChildSnapshot.key;

              // remove image associated with food from storage
              var imagePath = foodChildSnapshot.val().imagePath;
              if(imagePath){
                bucket.file(imagePath).delete();
                console.log('Removed food item ' + foodKey + ' image ' + imagePath + ' from storage');
              }

              console.log('Removed food item ' + foodKey + ' for expired event ' + eventKey);
              return foodRef.child(foodKey).remove();
            });
          }).catch((err) => {
            console.log(err);
          });

  				// remove expired event, decrement numEvents on associated pin
  				pinsRef.child(pinId).child('numEvents').transaction((numEvents) => {
  					eventsRef.child(eventKey).remove();
  					if(numEvents) {
  						numEvents--;
  						console.log('Expired event ' + eventKey + ' for pin ' + pinId +
  						'. This pin now has ' + numEvents + ' event(s).');
  					}
  					return numEvents;
  				});
  			}
  		}
  		catch(err) {
  			console.log(err);
			res.status(404).end();
  		}
  	})
  })

  //send back response
  res.status(200).end();

});

exports.sendNotificationsForEventAdded = functions.database.ref('/events/{eventId}')
    .onCreate((snapshot, context) => {
      // Grab the current value of what was written to the Realtime Database.
      const event = snapshot.val();
      const eventId = context.params.eventId;
      const eventTimeStart = moment(event.timeStart).utcOffset("-07:00").format('H:mm');
      const eventTimeEnd = moment(event.timeStart + event.duration).utcOffset("-07:00").format('H:mm');
      console.log('New event added', eventId, event);

      // Get the list of users (with associated device notification tokens) and settings
      const getUsersPromise = admin.database().ref('/users').once('value');
      const getSettingsPromise = admin.database().ref('/settings')
          .orderByChild('receivePushNotifications').equalTo(true)
          .once('value');

      let tokens = [];

      return Promise.all([getUsersPromise, getSettingsPromise]).then(results => {
        let users = results[0].val();
        let settings = results[1].val();

        // Notification details
        const payload = {
          data: {
            title: 'Free food added in your area!',
            body: eventId
          }
        };

        // Sends to all users with a device token for Firebase Cloud Messaging,
        // push notifications enabled, and preference settings matching event details
        for (let userId in settings) {
          if (settings.hasOwnProperty(userId)) {
            let timeWindowStart = moment(settings[userId].timeWindowStart, 'HH:mm').format('H:mm');
            let timeWindowEnd = moment(settings[userId].timeWindowEnd, 'HH:mm').format('H:mm');

            if (isBetween(eventTimeStart, timeWindowStart, timeWindowEnd) || isBetween(eventTimeEnd, timeWindowStart, timeWindowEnd)) {
              // Event overlaps with user's preferred time range in settings
              let token = users[userId].instanceId;
              if (token) {
                tokens.push(token);
              }
            }
          }
        }
        console.log('Will send to these device tokens: ', tokens);

        // Send notifications to all tokens.
        return admin.messaging().sendToDevice(tokens, payload);
      }).then((response) => {
        // For each message check if there was an error.
        const tokensToRemove = [];
        response.results.forEach((result, index) => {
          const error = result.error;
          if (error) {
            console.error('Failure sending notification to', tokens[index], error);
            if (error.code === 'messaging/invalid-registration-token' ||
                error.code === 'messaging/registration-token-not-registered') {
              // TODO Cleanup the tokens who are not registered anymore via tokensToRemove
            }
          }
        });
        return Promise.all(tokensToRemove);
      });
    });

exports.getNumEvents = functions.https.onRequest((req, res) => {
  var pinsRef = admin.database().ref('/pins');
  var eventsRef = admin.database().ref('/events');

  var dateStart = req.query.start;
  var dateEnd = req.query.end;
  var pinId = req.query.pinId;

  var count = 0;

  eventsRef.orderByChild('pinId').equalTo(pinId).once('value').then((eventSnapshot) =>
    eventSnapshot.forEach((eventChildSnapshot) => {
      var eventDate = eventChildSnapshot.val().timeStart
      if (eventDate >= dateStart && eventDate <= dateEnd) {
        count++
      }
    })
  ).then(() => {
    var res_str = 'Pin ' + pinId + ' has ' + count + ' events active between ' + dateStart + ' and ' + dateEnd;
    console.log(res_str);
    return res.status(200).send({"count": count});
  }).catch((err) => {
    console.log(err);
    return res.status(404).end();
  });
});