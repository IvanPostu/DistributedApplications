package com.ivan.data_warehouse;

import com.ivan.common.models.ArticleModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ArticleDAO {

    private static final Logger logger = LogManager.getLogger(ArticleDAO.class);
    private static final String CREATE_TABLE_QUERY =
            "CREATE TABLE TB_ARTICLE"
                    + "(id INTEGER PRIMARY KEY , "
                    + "authorFullName TEXT, "
                    + "title TEXT,"
                    + "content TEXT, "
                    + "category TEXT);";
    private static final String INSERT_QUERY =
            "INSERT INTO TB_ARTICLE(authorFullName, title, content, category, id) "
                    + " VALUES(?, ?, ?, ?, ?)";
    private static final String SELECT_QUERY =
            "SELECT "
                    + " id, authorFullName, title, content, category"
                    + " FROM TB_ARTICLE LIMIT ? OFFSET ?";
    private static final String SELECT_BY_ID_QUERY =
            "SELECT "
                    + " id, authorFullName, title, content, category"
                    + " FROM TB_ARTICLE WHERE id=? LIMIT 1 OFFSET 0";
    private static final String UPDATE_QUERY =
            "UPDATE TB_ARTICLE"
                    + " SET authorFullName=?, title=?, content=?, category=? "
                    + " WHERE id=? ";
    private static final String DELETE_QUERY =
            "DELETE FROM TB_ARTICLE"
                    + " WHERE id=? ";
    private static final String DELETE_ALL_QUERY = "DELETE FROM TB_ARTICLE";

    private AtomicInteger idIncrementor;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.error(e);
        }
    }

    private static final ArticleDAO instance = new ArticleDAO();

    private Connection connection;

    private ArticleDAO() {
        try {
            idIncrementor = new AtomicInteger(0);

            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(5); // set timeout to 5 sec.
            statement.executeUpdate(CREATE_TABLE_QUERY);

        } catch (SQLException e) {
            logger.error(e);
        }
    }

    public static ArticleDAO getInstance() {
        return instance;
    }

    public boolean insert(ArticleModel[] articles) {
        try (PreparedStatement ps =
                connection.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {
            connection.setAutoCommit(false);

            for (ArticleModel a : articles) {
                ps.setString(1, a.getAuthorFullName());
                ps.setString(2, a.getTitle());
                ps.setString(3, a.getContent());
                ps.setString(4, a.getCategory());

                int id = idIncrementor.incrementAndGet();

                ps.setInt(5, id);

                int numRowsInserted = ps.executeUpdate();

                if (numRowsInserted != 1) {
                    connection.rollback();
                    return false;
                } else {
                    a.setId(idIncrementor.get());
                }
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            logger.error(e);
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    public boolean cleanAndInsert(ArticleModel[] articles, int newIdSeqNumber) {
        idIncrementor.set(newIdSeqNumber);

        try (PreparedStatement ps =
                connection.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS);
                Statement st = connection.createStatement();) {
            connection.setAutoCommit(false);

            st.executeUpdate(DELETE_ALL_QUERY);

            for (ArticleModel a : articles) {
                ps.setString(1, a.getAuthorFullName());
                ps.setString(2, a.getTitle());
                ps.setString(3, a.getContent());
                ps.setString(4, a.getCategory());

                int id = a.getId();

                ps.setInt(5, id);

                int numRowsInserted = ps.executeUpdate();

                if (numRowsInserted != 1) {
                    connection.rollback();
                    return false;
                } else {
                    a.setId(idIncrementor.get());
                }
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            logger.error(e);
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    public boolean delete(int id) {
        try (PreparedStatement ps =
                connection.prepareStatement(DELETE_QUERY, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, id);
            connection.setAutoCommit(false);

            int numRowsChanged = ps.executeUpdate();

            if (numRowsChanged != 1) {
                connection.rollback();
                return false;
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            logger.error(e);
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    public List<ArticleModel> select(int offset, int limit) {
        List<ArticleModel> result = new ArrayList<>(limit);

        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUERY)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);

            try (ResultSet resultSet = statement.executeQuery();) {
                while (resultSet.next()) {
                    ArticleModel model = new ArticleModel();
                    model.setId(resultSet.getInt("id"));
                    model.setTitle(resultSet.getString("title"));
                    model.setContent(resultSet.getString("content"));
                    model.setCategory(resultSet.getString("category"));
                    model.setAuthorFullName(resultSet.getString("authorFullName"));

                    result.add(model);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }

        return result;
    }

    public Optional<ArticleModel> select(int id) {
        ArticleModel result = null;

        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_QUERY)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery();) {
                while (resultSet.next()) {
                    ArticleModel model = new ArticleModel();
                    model.setId(resultSet.getInt("id"));
                    model.setTitle(resultSet.getString("title"));
                    model.setContent(resultSet.getString("content"));
                    model.setCategory(resultSet.getString("category"));
                    model.setAuthorFullName(resultSet.getString("authorFullName"));

                    result = model;
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }

        return Optional.ofNullable(result);
    }

    public boolean update(ArticleModel articleModel) {
        try (PreparedStatement ps =
                connection.prepareStatement(UPDATE_QUERY, Statement.RETURN_GENERATED_KEYS)) {
            connection.setAutoCommit(false);

            ps.setString(1, articleModel.getAuthorFullName());
            ps.setString(2, articleModel.getTitle());
            ps.setString(3, articleModel.getContent());
            ps.setString(4, articleModel.getCategory());
            ps.setInt(5, articleModel.getId());

            int numRowsInserted = ps.executeUpdate();

            if (numRowsInserted != 1) {
                connection.rollback();
                return false;
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            logger.error(e);
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

}
