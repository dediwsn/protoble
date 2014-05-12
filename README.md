protoble
========

experiment ranging proximity calculation using estimote, radiusnetwork and kalman filter

Radius      : https://github.com/RadiusNetworks
Estimote    : https://github.com/Estimote

Estimote and radius network library have their method of calculate proximity distance. Current conclusion, both of them 
have fluctuative range, which is radius network hlibrary more unstable of range value than estimote in this experiment. To
explore their library it is easily clone from github for radius, but everyone should to decompile java jar class for estimote.
This repository included java package from https://github.com/Estimote/Android-SDK.

The use of kalman filter usually for smoothing in object tracking (robotic application) or other areas. For this experiment
I used this library as reference https://github.com/ThomasDavine/kalman-filter. Not quitely sure if it is applicable to in
proximity ranging for ibeacon. I found that, for kalman filter usually historical data to compute next/future possible data.
But in this case, I just present data only as input, in other said maybe just like real time data. I hope I can be make improvement
of this as soon as possible.

I just adding small label like this (example: 
  
  Est(1.00m)- RN(2.00m) - KF(3.00m) 
  
  )
  
So the difference between can be observed easily. 
  
Est index mean it will compute and display estimote accuracy, RN for radius network and KF for kalman filter.
  
  
  



