package com.github.toyset.pixonictest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Schedule {

	private static class Task<V> implements ScheduledFuture<V>, Runnable {

		private static final int STATUS_ENQUEUED = 0;
		private static final int STATUS_RUNNING = 1;
		private static final int STATUS_CANCELLED = 2;
		private static final int STATUS_DONE = 3;
		
		
		private final LocalDateTime scheduledTime;
		private final Callable<V> callable;
		
		private volatile int status = STATUS_ENQUEUED;
		
		private Thread worker;
		
		private V result;
		private Exception error;
		
		
		public Task(LocalDateTime scheduledTime, Callable<V> callable) {
			this.scheduledTime = scheduledTime;
			this.callable = callable;
		}
		
		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(
				LocalDateTime.now().until(scheduledTime, ChronoUnit.NANOS),
				TimeUnit.NANOSECONDS
			);
		}

		@Override
		public int compareTo(Delayed o) {
			return scheduledTime.compareTo(((Task<?>)o).scheduledTime);
		}
		
		public void run() {
			
			synchronized (this) {
				if (status != STATUS_ENQUEUED) {
					return;
				}
				
				this.worker = Thread.currentThread();
				status = STATUS_RUNNING;
			}
			
			try {
				result = callable.call();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				error = e;
			}
			
			synchronized (this) {
				this.worker = null;
				
				if (Thread.interrupted()) {
					status = STATUS_CANCELLED;
				} else if (status != STATUS_CANCELLED) {
					status = STATUS_DONE;
				}
				
				notifyAll();
			}
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			
			switch (status) {
				case STATUS_CANCELLED:
					return true;
				case STATUS_DONE:
					return false;
				case STATUS_RUNNING:
					if (!mayInterruptIfRunning) {
						return false;
					}
					break;
				default:
					// Do nothing
			}
			
			synchronized (this) {
				switch (status) {
					case STATUS_CANCELLED:
						return true;
					case STATUS_DONE:
						return false;
					case STATUS_RUNNING:
						if (!mayInterruptIfRunning) {
							return false;
						}
						worker.interrupt();
						break;
					default:
						// Do nothing
				}
				
				status = STATUS_CANCELLED;
				notifyAll();
			}
			
			return true;
		}

		@Override
		public boolean isCancelled() {
			return status == STATUS_CANCELLED;
		}

		@Override
		public boolean isDone() {
			return isDone(status);
		}
		
		private static boolean isDone(int status) {
			return status == STATUS_CANCELLED || status == STATUS_DONE;
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {

			int currentStatus = status;
			
			if (isDone(currentStatus)) {
				return getResult(currentStatus);
			}
			
			synchronized (this) {
				currentStatus = status;
				while (!isDone(currentStatus)) {
					wait();
					currentStatus = status;
				}
			}
			
			return getResult(currentStatus);
		}

		@Override
		public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			
			int currentStatus = status;
			
			if (isDone(currentStatus)) {
				return getResult(currentStatus);
			}
			
			synchronized (this) {
				currentStatus = status;
				if (!isDone(currentStatus)) {
					unit.timedWait(this, timeout);
				}
			}
			
			return getResult(currentStatus);
		}
		
		private V getResult(int status) throws ExecutionException {
			
			if (status == STATUS_CANCELLED) {
				throw new CancellationException();
			}
			
			if (error != null) {
				throw new ExecutionException(error);
			}
			
			return result;
		}
		
	}
	
	private final DelayQueue<Task<?>> queue = new DelayQueue<>();
	private final Thread worker = new Thread(this::work);
	
	private volatile boolean shutdown = false;
	
	
	public Schedule() {
		worker.start();
	}
	
	private void work() {
		
		while (!(isShutdown() && queue.isEmpty())) {
			try {
				queue.take().run();
			} catch (Exception e) {
				// Ignore exceptions until shutdown
			}
		}
	}
	
	public synchronized <V> ScheduledFuture<V> schedule(LocalDateTime scheduledTime, Callable<V> callable) {
		
		if (shutdown) {
			throw new RejectedExecutionException();
		}
		
		Task<V> task = new Task<>(scheduledTime, callable);
		queue.put(task);
		
		return task;
	}
	
	public synchronized void shutdown() {
		shutdown = true;
	}
	
	public synchronized List<ScheduledFuture<?>> shutdownNow() {
		
		shutdown = true;
		
		ArrayList<ScheduledFuture<?>> result = new ArrayList<>(queue);
		queue.clear();
		
		worker.interrupt();
		
		return result;
	}
	
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		unit.timedJoin(worker, timeout);
		return !worker.isAlive();
	}
	
	public synchronized boolean isShutdown() {
		return shutdown;
	}
	
	public boolean isTerminated() {
		return !worker.isAlive();
	}
}
