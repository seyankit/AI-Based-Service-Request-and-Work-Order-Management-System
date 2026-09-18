package ph.edu.htcgsc.serviceportal.dao;
import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;
import ph.edu.htcgsc.serviceportal.model.ServiceRequestDraft;
import java.sql.*; import java.util.*;
public class ServiceRequestDraftDAO {
    public List<ServiceRequestDraft> list(int requesterId)throws SQLException{
        String sql="SELECT * FROM SERVICE_REQUEST_DRAFT WHERE Requester_ID=? ORDER BY Updated_At DESC"; List<ServiceRequestDraft> out=new ArrayList<>();
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,requesterId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(map(r));}} return out;
    }
    public ServiceRequestDraft create(int requesterId,CreateServiceRequestRequest d)throws SQLException{
        String sql="INSERT INTO SERVICE_REQUEST_DRAFT(Requester_ID,Requested_Category_ID,Preferred_Priority,Title,Description,Location,Date_Reported) VALUES(?,?,?,?,?,?,?)";
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){set(p,requesterId,d);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){if(!k.next())throw new SQLException("Draft ID missing");return get(c,k.getLong(1),requesterId);}}
    }
    public ServiceRequestDraft update(long id,int requesterId,CreateServiceRequestRequest d)throws SQLException{
        String sql="UPDATE SERVICE_REQUEST_DRAFT SET Requested_Category_ID=?,Preferred_Priority=?,Title=?,Description=?,Location=?,Date_Reported=? WHERE Draft_ID=? AND Requester_ID=?";
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement(sql)){if(d.getRequestedCategoryId()==null)p.setNull(1,Types.INTEGER);else p.setInt(1,d.getRequestedCategoryId());p.setString(2,d.getPreferredPriority());p.setString(3,d.getTitle());p.setString(4,d.getDescription());p.setString(5,d.getLocation());if(d.getDateReported()==null)p.setNull(6,Types.DATE);else p.setDate(6,java.sql.Date.valueOf(d.getDateReported()));p.setLong(7,id);p.setInt(8,requesterId);if(p.executeUpdate()!=1)return null;return get(c,id,requesterId);}
    }
    public boolean delete(long id,int requesterId)throws SQLException{try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM SERVICE_REQUEST_DRAFT WHERE Draft_ID=? AND Requester_ID=?")){p.setLong(1,id);p.setInt(2,requesterId);return p.executeUpdate()==1;}}
    private void set(PreparedStatement p,int requesterId,CreateServiceRequestRequest d)throws SQLException{p.setInt(1,requesterId);if(d.getRequestedCategoryId()==null)p.setNull(2,Types.INTEGER);else p.setInt(2,d.getRequestedCategoryId());p.setString(3,d.getPreferredPriority());p.setString(4,d.getTitle());p.setString(5,d.getDescription());p.setString(6,d.getLocation());if(d.getDateReported()==null)p.setNull(7,Types.DATE);else p.setDate(7,java.sql.Date.valueOf(d.getDateReported()));}
    private ServiceRequestDraft get(Connection c,long id,int requesterId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT * FROM SERVICE_REQUEST_DRAFT WHERE Draft_ID=? AND Requester_ID=?")){p.setLong(1,id);p.setInt(2,requesterId);try(ResultSet r=p.executeQuery()){return r.next()?map(r):null;}}}
    private ServiceRequestDraft map(ResultSet r)throws SQLException{ServiceRequestDraft d=new ServiceRequestDraft();d.setDraftId(r.getLong("Draft_ID"));int cat=r.getInt("Requested_Category_ID");d.setRequestedCategoryId(r.wasNull()?null:cat);d.setPreferredPriority(r.getString("Preferred_Priority"));d.setTitle(r.getString("Title"));d.setDescription(r.getString("Description"));d.setLocation(r.getString("Location"));java.sql.Date reportedDate=r.getDate("Date_Reported");d.setDateReported(reportedDate==null?null:reportedDate.toString());d.setCreatedAt(r.getTimestamp("Created_At").toInstant().toString());d.setUpdatedAt(r.getTimestamp("Updated_At").toInstant().toString());return d;}
}
