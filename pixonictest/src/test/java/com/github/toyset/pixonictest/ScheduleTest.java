package com.github.toyset.pixonictest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class ScheduleTest {

	private static Callable<Void> task(int id, List<Integer> result) {
		return () -> {
			result.add(id);
			return null;
		};
	}
	
	@Test
	public void schedule() throws Exception {
		
		List<Integer> expected = Arrays.asList(1, 2, 3, 4);
		List<Integer> result = Collections.synchronizedList(new ArrayList<>());
		
		Schedule schedule = new Schedule();
		try {
			LocalDateTime currentTime = LocalDateTime.now();
			
			schedule.schedule(currentTime.plus(200, ChronoUnit.MILLIS), task(4, result));
			schedule.schedule(currentTime.plus(100, ChronoUnit.MILLIS), task(3, result));
			schedule.schedule(currentTime.minusSeconds(2), task(1, result));
			schedule.schedule(currentTime.minusSeconds(1), task(2, result));
			
			schedule.shutdown();
			
			Assert.assertTrue(schedule.awaitTermination(5, TimeUnit.SECONDS));
		} catch (Exception e) {
			schedule.shutdownNow();
			throw e;
		}
		
		Assert.assertEquals(expected, result);
	}
}
