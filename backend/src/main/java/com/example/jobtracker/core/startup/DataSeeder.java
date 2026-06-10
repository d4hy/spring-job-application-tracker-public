package com.example.jobtracker.core.startup;

import com.example.jobtracker.core.constants.TrackerConstants;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.repository.UserRepository;
import com.example.jobtracker.feature.tracking.repository.BoardRepository;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import com.example.jobtracker.feature.tracking.repository.JobApplicationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Startup data bootstrapper for local/demo environments.
 * Seeds initial users, boards, and columns after application startup so developers can
 * run the system with a usable baseline dataset immediately.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DataSeeder {
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      BoardRepository boardRepository,
                      BoardColumnRepository boardColumnRepository,
                      JobApplicationRepository jobApplicationRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    
    
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        // Check if data already exists
        if (userRepository.count() > 0) {
            return;
        }

        // Create demo user
        User user = new User("demo", passwordEncoder.encode("demo123"), "ROLE_USER");
        
        userRepository.save(user);

        BoardColumn[] internshipColumns = createBoardWithColumns(TrackerConstants.INTERNSHIPS_BOARD_NAME, user);
        createBoardWithColumns(TrackerConstants.FULL_TIME_JOBS_BOARD_NAME, user);

        // Create sample job applications
        // LEARNING NOTE: While we could batch insert, demonstrating entity approach
        // For production with large datasets, use insertMany pattern from Next.js
        int[][] jobData = {
            {0, 2}, // 2 jobs in Wish List
            {1, 3}, // 3 jobs in Applied
            {2, 1}, // 1 job in Interviewing
            {3, 1}, // 1 job in Offer
        };

        int jobId = 0;
        String[] companies = {"Google", "Microsoft", "Amazon", "Apple", "Meta", "Netflix", "Tesla"};
        String[] titles = {"Senior Software Engineer", "Full Stack Developer", "Backend Engineer", "DevOps Engineer"};

        for (int[] data : jobData) {
            int columnIndex = data[0];
            int count = data[1];
            BoardColumn column = internshipColumns[columnIndex];

            for (int i = 0; i < count; i++) {
                
                JobApplication job = new JobApplication(
                        companies[jobId % companies.length],
                        titles[jobId % titles.length],
                        i * TrackerConstants.JOB_ORDER_STEP,
                        column
                );
                
                job.setLocation("San Francisco, CA");
                
                job.setSalary("$150K - $250K");
                
                job.setJobUrl("https://example.com/jobs/" + jobId);
                
                jobApplicationRepository.save(job);
                jobId++;
            }
        }
    }

    private BoardColumn[] createBoardWithColumns(String boardName, User user) {
        Board board = new Board(boardName, user);
        boardRepository.save(board);

        List<String> columnNames = TrackerConstants.DEFAULT_COLUMN_NAMES;
        BoardColumn[] columns = new BoardColumn[columnNames.size()];

        for (int i = 0; i < columnNames.size(); i++) {
            BoardColumn column = new BoardColumn(columnNames.get(i), i, board);
            boardColumnRepository.save(column);
            columns[i] = column;
        }

        return columns;
    }
}
