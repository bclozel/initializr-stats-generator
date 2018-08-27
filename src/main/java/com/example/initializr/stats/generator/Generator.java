package com.example.initializr.stats.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class Generator {

	private static final Random random = new Random();

	private final List<DataSet> dataSets;

	private final List<Release> releases;

	private final List<Event> events;

	public Generator(List<DataSet> dataSets,
			List<Release> releases, List<Event> events) {
		this.dataSets = new ArrayList<>(dataSets);
		this.releases = new ArrayList<>(releases);
		this.events = new ArrayList<>(events);
	}


	public GenerationStatistics generateStatistics(DateRange range) {
		return new RandomGenerator(range).generate();
	}

	public List<DataSet> getDataSets(DateRange dateRange) {
		return this.dataSets.stream()
				.filter(dataSetMatch(dateRange))
				.collect(Collectors.toList());
	}

	public List<Release> getReleases(DateRange dateRange) {
		return this.releases.stream()
				.filter(releaseMatch(dateRange))
				.collect(Collectors.toList());
	}

	public List<Event> getEvents(DateRange dateRange) {
		return this.events.stream()
				.filter(eventMatch(dateRange))
				.collect(Collectors.toList());
	}

	public List<String> getTopIps(DateRange dateRange) {
		return IntStream.range(0, 10).mapToObj((i) -> randomIp())
				.collect(Collectors.toList());
	}

	private String randomIp() {
		StringBuilder sb = new StringBuilder("10.");
		sb.append(random.nextInt(255)).append(".")
				.append(random.nextInt(255)).append(".")
				.append(random.nextInt(255));
		return sb.toString();
	}

	private MultiValueMap<LocalDate, Event> indexEvents(DateRange range) {
		MultiValueMap<LocalDate, Event> events = new LinkedMultiValueMap<>();
		getEvents(range).forEach((event) -> events.add(event.getDate(), event));
		return events;
	}

	private Predicate<DataSet> dataSetMatch(DateRange range) {
		return (dataSet) -> dataSet.getRange().match(range);
	}

	private Predicate<Release> releaseMatch(DateRange range) {
		return (release) -> release.getRange().match(range);
	}

	private Predicate<Event> eventMatch(DateRange range) {
		return (event) -> range.match(event.getDate());
	}


	private class RandomGenerator {

		private final DateRange range;

		private final List<Release> releases;

		private final List<DataSet> dataSets;

		private final MultiValueMap<LocalDate, Event> events;


		RandomGenerator(DateRange range) {
			this.range = range;
			this.releases = getReleases(range);
			this.dataSets = getDataSets(range);
			this.events = indexEvents(range);
			if (releases.isEmpty() || dataSets.isEmpty()) {
				throw new IllegalArgumentException("No available information for range " + range);
			}
		}

		GenerationStatistics generate() {
			Iterator<Release> releasesIt = releases.iterator();
			Release currentRelease = releasesIt.next();
			Iterator<DataSet> dataSetsIt = dataSets.iterator();
			DataSet currentDataSet = dataSetsIt.next();
			MultiValueMap<String, GenerationStatistics.Entry> entries = new LinkedMultiValueMap<>();
			LocalDate currentDay = range.getFrom();
			while (!currentDay.isAfter(range.getTo())) {
				if (!currentRelease.getRange().match(currentDay)) {
					if (!releasesIt.hasNext()) {
						throw new IllegalArgumentException("No release information for " + currentDay);
					}
					currentRelease = releasesIt.next();
				}
				if (!currentDataSet.getRange().match(currentDay)) {
					if (!dataSetsIt.hasNext()) {
						throw new IllegalArgumentException("No data information for " + currentDay);
					}
					currentDataSet = dataSetsIt.next();
				}
				int total = currentDataSet.getData().get(currentDay.getDayOfWeek());
				String next = currentRelease.getData().getNext();
				double currentRatio = (next != null ? 0.9 : 0.92);
				entries.add(currentRelease.getData().getCurrent(), randomValue(currentDay, total, currentRatio));
				if (currentRelease.getData().getMaintenance() != null) {
					double maintenanceRatio = (next != null ? 0.08 : 0.1);
					entries.add(currentRelease.getData().getMaintenance(),randomValue(currentDay, total, maintenanceRatio));
				}
				if (currentRelease.getData().getNext() != null) {
					double nextRatio = (next != null ? 0.02 : 0);
					entries.add(next, randomValue(currentDay, total, nextRatio));
				}
				currentDay = currentDay.plusDays(1);
			}
			return new GenerationStatistics(range, entries);
		}

		// very scientific measure
		private GenerationStatistics.Entry randomValue(LocalDate day, int total, double ratio) {
			double value = ratio * total;
			value = value - (value * 0.05);
			value = value + (value * random.nextFloat() / 10);
			return new GenerationStatistics.Entry(day, applyEvents(day, (int) value));
		}

		private int applyEvents(LocalDate date, int value) {
			List<Event> dateEvents = this.events.get(date);
			if (dateEvents != null) {
				int original = value;
				for (Event event : dateEvents) {
					value = event.getType().transformValue(original);
				}
			}
			return value;
		}
	}

}
