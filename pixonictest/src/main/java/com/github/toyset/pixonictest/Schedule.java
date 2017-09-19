package com.github.toyset.pixonictest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Schedule {

	private final ScheduledExecutorService executor;
	
	
	public Schedule(int corePoolSize) {
		executor = Executors.newScheduledThreadPool(corePoolSize);
	}
	
	/**
	 * Ставит задачу в очередь на выполнение. При невозможности однозначно преобразовать локальное время в глобальное
	 * (в период перехода на летнее/зимнее время) задача ставится на <i>ближайший больший</i> из подходящих моментов времени.
	 * <p>Предполагается, что разница в наносекундах между {@code scheduledTime}, и текущим временем не выходит за границы
	 * диапазона значений <b>long</b>. Т.о, допустимый диапазон ~ +/-290 лет, что достаточно для практических задач.
	 * @param scheduledTime
	 * @param callable
	 * @return
	 */
	public <V> ScheduledFuture<V> schedule(LocalDateTime scheduledTime, Callable<V> callable) {
		
		Instant cheduledInstant =
			scheduledTime.atZone(ZoneId.systemDefault()).withLaterOffsetAtOverlap().toInstant();
		
		Instant currentInstant = Instant.now();
		
		return executor.schedule(callable,
			ChronoUnit.NANOS.between(currentInstant, cheduledInstant), TimeUnit.NANOSECONDS);
	}
	
	public void shutdown() {
		executor.shutdown();
	}
	
	public List<Runnable> shutdownNow() {
		return executor.shutdownNow();
	}
	
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return executor.awaitTermination(timeout, unit);
	}
	
	public boolean isShutdown() {
		return executor.isShutdown();
	}
	
	public boolean isTerminated() {
		return executor.isTerminated();
	}
}
