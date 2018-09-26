/*
433 MHz SmartThings Gateway - Version 1.0

This javascript uses NodeJs functionality to provide a gateway between SmartThings and 433 devices.

This Software Package contains:
	1. SmartThings Device Handlers.  Install it to the SmartThings Developer API.
		a. 443-STDHL_v1.groovy.  
	2. SmartThings Gateway software.  Run (.a) on NodeJS server
		a. 433-ST-Gateway_v1.js
		b. devices-states_v1.js
		c. devices-states.json

This gateway relies on the external 433 code sender (software and hardware) to emit the code.  The command to emit the code should be available as a shell script.

09-21-2018	Release of Version 1 Hub
*/

//##### Options for this program ###################################
var logFile = "yes";	//	Set to no to disable error.log file.
var hubPort = 8083;	//	Synched with Device Handlers.
//##################################################################

//---- Determine if old Node version, act accordingly -------------
console.log("Node.js Version Detected:   " + process.version);
var oldNode = "no";
if (process.version == "v6.0.0-pre") {
	oldNode ="yes";
	logFile = "no";
}
// full path to error log file
var errorLogPath = __dirname + "/error.log";

//---- Program set up and global variables -------------------------
var http = require('http');
var fs = require('fs');
var server = http.createServer(onRequest);
var devices_States = require('./devices-states');

//---- Start the HTTP Server Listening to SmartThings --------------
server.listen(hubPort);
console.log("433 SmartThings Gateway Console Log");
logResponse("\n\r" + new Date() + "\r433 SmartThings Gateway Error Log");

//---- Command interface to Smart Things ---------------------------
function onRequest(request, response){
	var gatewayCommand = request.headers["gateway-command"];
	var deviceID = request.headers["device-id"];

	var cmdRcvd = "\n\r" + new Date() + "\r\nGateway command received: " + gatewayCommand + " from device " + deviceID;
	console.log(cmdRcvd);
	
	response.setHeader("gateway-commmand", gatewayCommand);
	response.setHeader("deviceID", deviceID);
	var rfcode = parseInt(deviceID, 10);
		
	switch(gatewayCommand) {
		
		case "setState-on":
			send_RF(rfcode);
			devices_States.setDeviceState(deviceID, "on");
			response.setHeader("gateway-device-status", "on");
			response.setHeader("gateway-response", "OK");
		break;
		
		case "setState-off":
			send_RF(rfcode+9);
			devices_States.setDeviceState(deviceID, "off");
			response.setHeader("gateway-device-status", "off");
			response.setHeader("gateway-response", "OK");
			
		break;
		
		case "getStatus":			
			response.setHeader("gateway-device-status", devices_States.getDeviceState(deviceID));
			response.setHeader("gateway-response", "OK");
		break;

		default:
			response.setHeader("gateway-response", "InvalidGatewayCommand");
			response.end();
			var respMsg = "#### Invalid Command ####";
			var respMsg = new Date() + "\n\r#### Invalid Gateway command from device " + deviceID + " ####\n\r";
			console.log(respMsg);
			logResponse(respMsg);
	}
	response.end()
	console.log("============= End of process. ================");
}

function send_RF(rfcode){
	console.log("Sending code " + rfcode);
	var shell_command = "/home/pi/sendcodes.sh " + rfcode;
		const { exec } = require('child_process');
		exec(shell_command, (err, stdout, stderr) => {
			if (err) {
				// node couldn't execute the command
				console.log("Can't execute command " + shell_command);
				//response.setHeader("cmd-response", 'UnableToExecuteCommand');
				return;
			}
		});
		console.log("Command succeeded: " + shell_command);
}

//----- Utility - Response Logging Function ------------------------
function logResponse(respMsg) {
	if (logFile == "yes") {
		fs.appendFileSync(errorLogPath, "\r" + respMsg)
	}
}
