package com.qiniu.hugo.pili_sever;

import com.qiniu.pili.Client;
import com.qiniu.pili.Hub;
import com.qiniu.pili.Meeting;

import java.util.Date;

public class myClass {
    // Replace with your keys here
    private static final String ACCESS_KEY = "abc"; //填入你自己的ACCESS_KEY
    private static final String SECRET_KEY = "acd";  //填入你自己的SECRET_KEY
    // Replace with your hub name
    private static final String HUB_NAME = "lipengv2"; // 填入你们自己的直播空间名称
    private static Meeting meeting;
    public static void main(String[] args ){

        // Instantiate an Hub object
        Client credentials = new Client(ACCESS_KEY, SECRET_KEY); // Credentials Object
        //初始化Hub
        Hub hub = credentials.newHub(HUB_NAME);
        meeting = credentials.newMeeting();
        //meeting = new Meeting(credentials); XXXX
        //meeting.createRoom()

            // create room with name
            try {
                String roomName = "test12Room";
                String r1 =  meeting.createRoom("123",roomName,12);


                Meeting.Room room = meeting.getRoom(roomName);
                System.out.println("roomName:"+r1);


            } catch (Exception e){
                e.printStackTrace();
            }


            try {
                String token = meeting.roomToken("room1", "123", "admin", new Date(1785600000000L));
                System.out.println("token==>  "+token);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
}
