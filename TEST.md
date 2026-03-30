# 12288

## Pess

  █ TOTAL RESULTS                                                                                                                                                                                                    
                                                                                                                                                                                                                     
    checks_total.......: 4388631 13758.111271/s                                                                                                                                                                      
    checks_succeeded...: 80.21%  3520249 out of 4388631                                                                                                                                                              
    checks_failed......: 19.78%  868382 out of 4388631                                                                                                                                                               
                                                                                                                                                                                                                     
    ✗ 쿠폰 발행 응답 허용                                                                                                                                                                                            
      ↳  1% — ✓ 9343 / ✗ 868382                                                                                                                                                                                      
    ✓ 쿠폰 발행 성공 시 ID 존재                                                                                                                                                                                      
    ✓ 쿠폰 발행 성공 시 storeId 일치                                                                                                                                                                                 
    ✓ 쿠폰 발행 성공 시 userId 일치                                                                                                                                                                                  
    ✓ 쿠폰 발행 성공 시 issuedAt 존재                                                                                                                                                                                
    ✓ 통계 조회 성공                                                                                                                                                                                                 
    ✓ 통계 응답 storeId 일치                                                                                                                                                                                         
    ✓ 통계 응답 총 발급 수량 일치                                                                                                                                                                                    
    ✓ 통계 응답 발급 수량이 목표 수량과 일치                                                                                                                                                                         
    ✓ 통계 응답 잔여 수량 0                                                                                                                                                                                          
    ✓ 통계 응답 상위 유저 중복 발급 없음                                                                                                                                                                             
                                                                                                                                                                                                                     
    CUSTOM                                                                                                                                                                                                           
    issue_coupon_failure_rate.......: 98.93% 868382 out of 877725                                                                                                                                                    
    issue_coupon_success_duration...: avg=21.38s min=513.07ms med=22.28s max=50.57s p(90)=29.44s p(95)=29.83s                                                                                                        
    issue_coupon_success_rate.......: 1.06%  9343 out of 877725                                                                                                                                                      
                                                                                                                                                                                                                     
    HTTP                                                                                                                                                                                                             
    http_req_duration...............: avg=2.85s  min=855µs    med=1.6s   max=1m3s   p(90)=4.95s  p(95)=9.97s                                                                                                         
      { expected_response:true }....: avg=21.36s min=60.56ms  med=22.27s max=50.57s p(90)=29.44s p(95)=29.83s                                                                                                        
    http_req_failed.................: 98.93% 868382 out of 877732                                                                                                                                                    
    http_reqs.......................: 877732 2751.640437/s                                                                                                                                                           
                                                                                                                                                                                                                     
    EXECUTION                                                                                                                                                                                                        
    iteration_duration..............: avg=3.92s  min=924.16µs med=2.34s  max=1m8s   p(90)=8.07s  p(95)=11.22s                                                                                                        
    iterations......................: 877725 2751.618492/s                                                                                                                                                           
    vus.............................: 2      min=0                max=12288                                                                                                                                          
    vus_max.........................: 12288  min=12288            max=12288                                                                                                                                          
                                                                                                                                                                                                                     
    NETWORK                                                                                                                                                                                                          
    data_received...................: 263 MB 826 kB/s                                                                                                                                                                
    data_sent.......................: 219 MB 688 kB/s


## Redis 

  █ TOTAL RESULTS 

    checks_total.......: 1862976 5940.242817/s
    checks_succeeded...: 99.96%  1862356 out of 1862976
    checks_failed......: 0.03%   620 out of 1862976

    ✗ 쿠폰 발행 응답 허용                            
      ↳  99% — ✓ 371974 / ✗ 620
    ✓ 쿠폰 발행 성공 시 ID 존재
    ✓ 쿠폰 발행 성공 시 storeId 일치
    ✓ 쿠폰 발행 성공 시 userId 일치
    ✓ 쿠폰 발행 성공 시 issuedAt 존재
    ✓ 통계 조회 성공                                 
    ✓ 통계 응답 storeId 일치
    ✓ 통계 응답 총 발급 수량 일치
    ✓ 통계 응답 발급 수량이 목표 수량과 일치
    ✓ 통계 응답 잔여 수량 0
    ✓ 통계 응답 상위 유저 중복 발급 없음

    CUSTOM                                           
    issue_coupon_failure_rate.......: 0.16%  620 out of 372594
    issue_coupon_success_duration...: avg=9.76s min=923µs  med=10.88s max=30.72s p(90)=17.19s p(95)=19.34s
    issue_coupon_success_rate.......: 99.83% 371974 out of 372594

    HTTP                                             
    http_req_duration...............: avg=9.8s  min=923µs  med=10.9s  max=30.72s p(90)=17.22s p(95)=19.52s
      { expected_response:true }....: avg=9.76s min=923µs  med=10.88s max=30.72s p(90)=17.19s p(95)=19.34s
    http_req_failed.................: 0.16%  620 out of 372601
    http_reqs.......................: 372601 1188.067057/s

    EXECUTION                                        
    iteration_duration..............: avg=9.85s min=1.09ms med=10.91s max=30.72s p(90)=17.28s p(95)=19.6s 
    iterations......................: 372594 1188.044737/s
    vus.............................: 20     min=0                max=12288
    vus_max.........................: 12288  min=11792            max=12288

    NETWORK                                          
    data_received...................: 99 MB  314 kB/s
    data_sent.......................: 92 MB  294 kB/s
