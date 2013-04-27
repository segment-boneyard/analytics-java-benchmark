package io.segment.benchmark;

import java.io.FileWriter;
import java.io.IOException;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.joda.time.DateTime;

import com.github.segmentio.Analytics;
import com.github.segmentio.models.Context;
import com.github.segmentio.models.EventProperties;
import com.github.segmentio.models.Providers;
import com.github.segmentio.stats.AnalyticsStatistics;
import com.google.common.util.concurrent.RateLimiter;

public class JavaClientBenchmark {
	 
	  /**
		 * @param args
		 */
		public static void main(String[] args) {
			
			runTest(50, 60000, 1000);
		}
		
		public static void runTest(int requestsPerSecond, int durationMs, int sampleTime) {
	 
			Sigar sigar = new Sigar();
	 
			String filename = "benchmark" + (int)Math.floor(Math.random() * 1000) + ".csv";
			
		    FileWriter writer = null;
			try {
				writer = new FileWriter(filename);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			// long pid = sigar.getPid();
			
			Analytics.initialize("testsecret");
			
			RateLimiter rate = RateLimiter.create(requestsPerSecond);
			
			long start = System.currentTimeMillis();
			long lastSample = 0;
			
			AnalyticsStatistics statistics = Analytics.getStatistics();
			double insertTime = 0;
			int count = 0;
	 
			System.out.println("Test starting with filename " + filename + " ...");
			
			String headings = "CPU,Memory,Inserted,Successful,Failed,Queue Size,Insert Time (avg ms)";
			
			System.out.println(headings);	

			try {
				writer.write(headings + "\n");
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			while(System.currentTimeMillis() - start <= durationMs) {
				
				if (rate.tryAcquire()) {
					String userId = "benchmark" + (int)Math.floor(Math.random() * 1000);
					
					long insertStart = System.currentTimeMillis();
					
					Analytics.track(userId, "Benchmark", new EventProperties()
							.put("name", "Achilles")
							.put("shippingMethod", "2-day"),
							new DateTime(),
							new Context()
								.setIp("192.168.1.1")
								.setProviders(new Providers()
									.setDefault(true)
									.setEnabled("Mixpanel", false)
									.setEnabled("KISSMetrics", true)
									.setEnabled("Google Analytics", true)
								));
					
					long insertDuration = System.currentTimeMillis() - insertStart;
					insertTime += insertDuration;
					count += 1;
				}
				
				if (lastSample == 0 || System.currentTimeMillis() - lastSample >= sampleTime) {
					// its time to take a sample
					
					try {
						
						System.gc();
						
						double cpuUsed = sigar.getCpuPerc().getCombined();
						long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
						
						int successfulRequests = statistics.getSuccessful().getCount();
						int failedRequests = statistics.getFailed().getCount();
						int insertedRequests = statistics.getInserted().getCount();
						int queueSize = (int)statistics.getQueued().getLast();
						
						String line = cpuUsed + "," + memoryUsed + "," + insertedRequests + 
								"," + successfulRequests + "," + failedRequests + "," + queueSize + 
								"," + (insertTime / count);
						
						System.out.println(line);
						
						try {
							writer.write(line + "\n");
							writer.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
						
					} catch (SigarException e) {
						e.printStackTrace();
					}
					
					
					lastSample = System.currentTimeMillis();
				}	
			}
			
			System.out.println("Test finished.");
			
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Analytics.close();
		}
	 
		
	}