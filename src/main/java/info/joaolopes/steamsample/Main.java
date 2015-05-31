/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package info.joaolopes.steamsample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends;
import uk.co.thomasc.steamkit.steam3.handlers.steamgamecoordinator.SteamGameCoordinator;
import uk.co.thomasc.steamkit.steam3.handlers.steamtrading.SteamTrading;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOffCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOnCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoginKeyCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.UpdateMachineAuthCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.MachineAuthDetails;
import uk.co.thomasc.steamkit.steam3.steamclient.SteamClient;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.JobCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.ConnectedCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.DisconnectedCallback;
import uk.co.thomasc.steamkit.types.JobID;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper;

/**
 *
 * @author jocolopes
 */
public class Main {
    public String username;
    public String password;
    
    
    public SteamFriends steamFriends;
    public SteamClient steamClient;
    public SteamTrading steamTrade;
    public SteamGameCoordinator steamGC;
    public SteamUser steamUser;
    public LogOnDetails logOnDetails;
    public CallbackMsg cbMsg;
    public Boolean isLoggedIn = false;
    
    public static void main(String[] args) {
        new Main();
    }
    
    public Main() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Insert username:");
        try {
            this.username = br.readLine();
        } catch (IOException ioe) {
            System.out.println("IO error trying to read your name!");
            System.exit(1);
        }
        
        System.out.println("Insert password:");
        try {
            this.password = br.readLine();
        } catch (IOException ioe) {
            System.out.println("IO error trying to read your password!");
            System.exit(1);
        }
        
        steamClient = new SteamClient();
        steamTrade = steamClient.getHandler(SteamTrading.class);
        steamUser = steamClient.getHandler(SteamUser.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);
        steamGC = steamClient.getHandler(SteamGameCoordinator.class);
        logOnDetails = new LogOnDetails().username(username).password(password);
        logOnDetails.sentryFileHash = null;
        
        readSentryFile();
        
        steamClient.connect();
        
        while (true) {
            Update();
        }
    }
    
    private void readSentryFile() {
        File sentryFile = new File(logOnDetails.username + ".sentryfile");
        byte[] result = new byte[(int)sentryFile.length()];
        if(sentryFile.exists() && sentryFile.length() > 0){
            
            System.out.println("Reading sentry file: " + logOnDetails.username + ".sentryfile");
            try {
                InputStream input = null;
                try {
                  int totalBytesRead = 0;
                  input = new BufferedInputStream(new FileInputStream(sentryFile));
                  while(totalBytesRead < result.length){
                    int bytesRemaining = result.length - totalBytesRead;
                    //input.read() returns -1, 0, or more :
                    int bytesRead = input.read(result, totalBytesRead, bytesRemaining); 
                    if (bytesRead > 0){
                      totalBytesRead = totalBytesRead + bytesRead;
                    }
                  }
                  /*
                   the above style is a bit tricky: it places bytes into the 'result' array; 
                   'result' is an output parameter;
                   the while loop usually has a single iteration only.
                  */
                }
                finally {
                  logOnDetails.sentryFileHash = CryptoHelper.SHAHash(result);
                  input.close();
                }
              }
              catch (FileNotFoundException ex) {
                  System.out.println("File not found");
              }
              catch (IOException ex) {
                  System.out.println("Error: " + ex.getMessage());
              }
        }
    }
    
    public void Update() {
        while (true) {
            cbMsg = steamClient.getCallback(true);
            if (cbMsg == null) {
                    try {
                            Thread.sleep(500);
                    } catch (final InterruptedException e) {
                            e.printStackTrace();
                    }
                    break;
            }

            handleSteamMessage(cbMsg);
        }
    }
    
     void OnUpdateMachineAuthCallback(UpdateMachineAuthCallback machineAuth, JobID jobid){
        byte[] hash = CryptoHelper.SHAHash(machineAuth.getData());
        
        System.out.println("Writing sentry file...");

        try {
          OutputStream output = null;
          try {
            output = new BufferedOutputStream(new FileOutputStream(logOnDetails.username + ".sentryfile"));
            output.write(machineAuth.getData());
          }
          finally {
            output.close();
          }
        }
        catch(FileNotFoundException ex){
            System.out.println("File Not Found");
        }
        catch(IOException ex){
            System.out.println("Error: " + ex.getMessage());
        }

        MachineAuthDetails mad = new MachineAuthDetails();
        mad.bytesWritten = machineAuth.getBytesToWrite();
        mad.fileName = machineAuth.getFileName();
        mad.fileSize = machineAuth.getBytesToWrite();
        mad.offset = machineAuth.getOffset();

        mad.oneTimePassword = machineAuth.getOneTimePassword();

        mad.sentryFileHash = hash;
        mad.lastError = 0;

        mad.result = EResult.OK;
        mad.jobId = jobid.getValue();

        steamUser.sendMachineAuthResponse(mad);
    }
    
    public void handleSteamMessage(CallbackMsg msg) {
        msg.handle(ConnectedCallback.class, new ActionT<ConnectedCallback>() {
            @Override
            public void call(ConnectedCallback callback) {
                System.out.println("Connection Status " + callback.getResult());

                if (callback.getResult() == EResult.OK) {
                    steamUser.logOn(logOnDetails);

                } else {
                    System.out.println("Failed to Connect to the steam community");
                    steamClient.connect();
                }

            }
        });
        
        if(msg.getClass().getName().endsWith("JobCallback")){
            JobCallback<?> jcb = (JobCallback<?>) msg;
            if(jcb.getCallbackType().getName().endsWith("UpdateMachineAuthCallback")){
                UpdateMachineAuthCallback umc = (UpdateMachineAuthCallback) jcb.getCallback();
                OnUpdateMachineAuthCallback(umc, jcb.getJobId());
            }
        }
        
        msg.handle(LoggedOnCallback.class, new ActionT<LoggedOnCallback>() {
            @Override
            public void call(LoggedOnCallback callback) {

                if (callback.getResult() != EResult.OK) {
                    System.out.println("Login Failure: " + callback.getResult());
                }

                if(callback.getResult() == EResult.AccountLogonDenied){
                    
                    System.out.println("This account is protected by Steam Guard.  Enter the authentication code sent to the proper email.");
                    
                    try{
                        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
                        String s = bufferRead.readLine();
                        logOnDetails.authCode(s);
                        steamClient.disconnect();
                    }
                    catch(IOException e)
                    {
                        e.printStackTrace();
                    }
                }


                if (callback.getResult() == EResult.InvalidLoginAuthCode)
                {
                    System.out.println("An Invalid Authorization Code was provided.  Enter the authentication code sent to the proper email: ");
                    
                    try{
                        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
                        String s = bufferRead.readLine();
                        logOnDetails.authCode(s);
                        steamClient.disconnect();

                    }
                    catch(IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });
        
        
        msg.handle(LoggedOffCallback.class, new ActionT<LoggedOffCallback>() {
            @Override
            public void call(LoggedOffCallback callback) {
                System.out.println("Told to log off by server (" + callback.getResult() + "), attemping to reconnect");
                steamClient.connect();
            }
        });

        msg.handle(DisconnectedCallback.class, new ActionT<DisconnectedCallback>() {
            @Override
            public void call(DisconnectedCallback obj) {
                isLoggedIn = false;
                System.out.println("Disconnected from Steam Network, attemping to reconnect");
                steamClient.connect();
            }
        });
        
        msg.handle(LoginKeyCallback.class, new ActionT<LoginKeyCallback>() {
            @Override
            public void call(LoginKeyCallback callback) {
                System.out.println("Logged in");
                steamFriends.setPersonaName("[Pr00fSample] JavaSampleBot");
                steamFriends.setPersonaState(EPersonaState.LookingToTrade);

                isLoggedIn = true;
            }
        });
    }
    
}
