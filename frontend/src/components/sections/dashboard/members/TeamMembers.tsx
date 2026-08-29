// Team member Images
import { Avatar, Box, Button, Card, Chip, Divider, Grid, Stack, Typography } from '@mui/material';
import person1 from 'assets/images/team/team-1.jpg';
import person2 from 'assets/images/team/team-2.jpg';
import person3 from 'assets/images/team/team-3.jpg';
import person4 from 'assets/images/team/team-4.jpg';
import { Fragment } from 'react';

// Team member Data
/* -------------------------------------------------------------------------- */
interface TeamMember {
  id: number;
  img: string;
  name: string;
  status: string;
  badge: 'success' | 'warning' | 'error' | 'default' | 'primary' | 'secondary' | 'info';
}
const data: TeamMember[] = [
  { id: 1, img: person1, name: 'John Michael', status: 'online', badge: 'success' },
  { id: 2, img: person2, name: 'Alex Smith', status: 'in meeting', badge: 'warning' },
  { id: 3, img: person3, name: 'Samantha Ivy', status: 'offline', badge: 'error' },
  { id: 4, img: person4, name: 'John Michael', status: 'online', badge: 'success' },
];
/* -------------------------------------------------------------------------- */
const TeamMembers = () => {
  const lastItemId = data[data.length - 1].id;

  return (
    <Card sx={{ height: 1, overflow: 'hidden', color: 'dark.main' }}>
      <Box sx={{ p: 3 }}>
        <Typography variant="h5" textTransform="capitalize" fontWeight="medium">
          Team Members
        </Typography>
      </Box>
      <Box sx={{ pb: 3, px: 3 }}>
        <Stack component="ul" direction="column" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {data.map(({ id, img, name, badge, status }) => (
            <Fragment key={id}>
              <Box component="li" sx={{ py: 1 }}>
                <Grid container spacing={{ xs: 1.5, sm: 3 }} alignItems="center">
                  <Grid item alignItems="center">
                    <Avatar src={img} alt={name} />
                  </Grid>
                  <Grid item lineHeight={1.4}>
                    <Typography variant="h6" color="dark.main">
                      {name}
                    </Typography>
                    <Chip
                      label={status}
                      sx={{ bgcolor: `${badge}.light`, color: `${badge}.dark` }}
                    />
                  </Grid>
                  <Grid item ml="auto">
                    <Button variant="outlined" color="primary" size="small">
                      Add
                    </Button>
                  </Grid>
                </Grid>
              </Box>

              {id !== lastItemId && (
                <Divider
                  sx={{
                    borderTop: 1,
                    borderTopColor: 'text.disabled',
                    m: 0,
                    p: 0,
                    opacity: 0.5,
                  }}
                />
              )}
            </Fragment>
          ))}
        </Stack>
      </Box>
    </Card>
  );
};

export default TeamMembers;
