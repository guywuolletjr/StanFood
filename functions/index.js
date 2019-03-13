const functions = require('firebase-functions');

const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

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

        // Currently sends to all users with a device token for Firebase Cloud Messaging
        // and push notifications enabled within settings.
        // TODO: Once we implement settings we should only send to users with preferences
        // that match the event
        for (let userId in settings) {
          if (settings.hasOwnProperty(userId)) {
            let token = users[userId].instanceId;
            if (token) {
              tokens.push(token);
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
